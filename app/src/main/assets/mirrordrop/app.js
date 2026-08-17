// MirrorDrop receiver client. No build step - plain ES2017, served directly from the mirror's
// embedded HTTP server (see MirrorDropServer.kt). Kept as one file for now; split up if/when it
// grows unwieldy across later phases (WebRTC, transfer, share modes).

const PROTOCOL_VERSION = 1;
const RECONNECT_DELAY_MS = 2000;

// Binary frame tags MirrorDropTransferManager writes onto the DataChannel (Kotlin side is the
// source of truth - see MirrorDropTransferManager.kt).
const FRAME_TAG_FILE_START = 1;
const FRAME_TAG_FILE_CHUNK = 2;
const FRAME_TAG_FILE_END = 3;

const statusEl = document.getElementById("status");
const deviceLineEl = document.getElementById("deviceLine");
const contentEl = document.getElementById("content");
const pinPromptEl = document.getElementById("pinPrompt");
const pinFormEl = pinPromptEl;
const pinInputEl = document.getElementById("pinInput");
const pinErrorEl = document.getElementById("pinError");

const shareToken = new URLSearchParams(location.search).get("token");
let enteredPin = new URLSearchParams(location.search).get("pin") || null;
// Set once a "no_active_session"/"invalid_token"/"session_expired" error arrives - these mean the
// link itself is dead, so retrying the same token in a loop would just spam the same rejection.
let terminalError = false;
// Set while the PIN prompt is up - suppresses the normal auto-reconnect-on-close behavior until
// the user actually submits a PIN, at which point a fresh connection attempt is made explicitly.
let awaitingPinEntry = false;

let socket = null;
let reconnectTimer = null;
let myPeerId = null;
let peerConnection = null;
let dataChannel = null;

// ShareSession auth failures (see MirrorDropShareSessionManager.kt) are delivered two ways: a best-
// effort SignalError message sent just before the server closes the socket, AND the WebSocket
// close frame's own `reason` field (set to the same code). The message can race the close on some
// clients, but the close reason is part of the same closing handshake frame and always arrives -
// so it's the authoritative source; the message is a redundant nicety when it does get through.
const AUTH_ERROR_MESSAGES = {
  pin_required: "",
  invalid_pin: "That PIN doesn't match.",
  no_active_session: "Sharing isn't active on this mirror right now.",
  invalid_token: "This share link is no longer valid.",
  session_expired: "This share link has expired.",
};

// fileId -> { transferId }, filled in on "transferReady" so a FILE_START frame (keyed only by the
// numeric transferId) can be matched back to the manifest entry the user clicked.
const pendingTransfers = new Map();
// transferId (string) -> { fileId, name, mimeType, size, sha256, chunks: Uint8Array[], received: number }
const activeTransfers = new Map();

function setStatus(text, isError) {
  statusEl.textContent = text;
  statusEl.classList.toggle("error-text", !!isError);
}

function send(message) {
  if (!socket || socket.readyState !== WebSocket.OPEN) return;
  socket.send(JSON.stringify(Object.assign({ protocolVersion: PROTOCOL_VERSION }, message)));
}

function connectSignaling() {
  if (terminalError) return;
  if (!shareToken) {
    setStatus("This link is missing its share code.", true);
    terminalError = true;
    return;
  }

  const proto = location.protocol === "https:" ? "wss:" : "ws:";
  const query = new URLSearchParams({ token: shareToken });
  if (enteredPin) query.set("pin", enteredPin);
  socket = new WebSocket(`${proto}//${location.host}/signal?${query.toString()}`);

  socket.addEventListener("open", () => {
    send({ type: "hello", deviceName: guessDeviceName() });
  });

  socket.addEventListener("message", (event) => {
    let message;
    try {
      message = JSON.parse(event.data);
    } catch (err) {
      return;
    }
    handleSignal(message);
  });

  socket.addEventListener("close", (event) => {
    if (event.reason && Object.prototype.hasOwnProperty.call(AUTH_ERROR_MESSAGES, event.reason)) {
      handleSignalError(event.reason, AUTH_ERROR_MESSAGES[event.reason]);
      return;
    }
    if (terminalError || awaitingPinEntry) return;
    setStatus("Disconnected - reconnecting…", true);
    scheduleReconnect();
  });

  socket.addEventListener("error", () => {
    // "close" always follows "error" for a WebSocket, so the actual UI update happens there.
  });
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectSignaling();
  }, RECONNECT_DELAY_MS);
}

function handleSignalError(code, message) {
  switch (code) {
    case "pin_required":
      awaitingPinEntry = true;
      showPinPrompt("");
      break;
    case "invalid_pin":
      awaitingPinEntry = true;
      showPinPrompt(message || "That PIN doesn't match.");
      break;
    case "no_active_session":
    case "invalid_token":
    case "session_expired":
      terminalError = true;
      hidePinPrompt();
      setStatus(message || "This share link no longer works.", true);
      break;
    default:
      setStatus(message || "Something went wrong", true);
  }
}

function showPinPrompt(errorText) {
  pinPromptEl.hidden = false;
  pinErrorEl.textContent = errorText;
  pinInputEl.value = "";
  pinInputEl.focus();
  setStatus("PIN required");
}

function hidePinPrompt() {
  pinPromptEl.hidden = true;
}

pinFormEl.addEventListener("submit", (event) => {
  event.preventDefault();
  const value = pinInputEl.value.trim();
  if (!value) return;
  enteredPin = value;
  awaitingPinEntry = false;
  hidePinPrompt();
  setStatus("Connecting to MirrorDash…");
  if (socket) socket.close();
  connectSignaling();
});

function handleSignal(message) {
  if (message.protocolVersion !== PROTOCOL_VERSION) {
    setStatus("This mirror is running a different MirrorDrop version.", true);
    socket.close();
    return;
  }
  switch (message.type) {
    case "welcome":
      myPeerId = message.peerId;
      hidePinPrompt();
      setStatus("Connected to MirrorDash");
      deviceLineEl.textContent = message.deviceName || "";
      break;
    case "offer":
      handleOffer(message.sdp);
      break;
    case "iceCandidate":
      if (peerConnection && message.candidate) {
        peerConnection.addIceCandidate({
          candidate: message.candidate,
          sdpMid: message.sdpMid || null,
          sdpMLineIndex: message.sdpMLineIndex != null ? message.sdpMLineIndex : null,
        }).catch(() => {});
      }
      break;
    case "manifest":
      renderManifest(message.files || []);
      break;
    case "transferReady":
      pendingTransfers.set(message.fileId, { transferId: message.transferId });
      break;
    case "transferComplete":
      // Informational only - the mirror has finished writing bytes to the DataChannel's send
      // buffer. The authoritative "this file is actually here and verified" signal on this side
      // is the FILE_END frame handled in handleDataChannelMessage, not this message.
      console.log("[mirrordrop] transferComplete", message.transferId, message.success);
      break;
    case "error":
      handleSignalError(message.code, message.message);
      break;
    default:
      break;
  }
}

// Android is always the offerer (see MirrorDropWebRtcManager) - the browser only ever answers
// and waits for the DataChannel Android creates via ondatachannel. LAN-only (brief §18/§31): no
// ICE servers configured, host candidates on the same Wi-Fi network are enough.
async function handleOffer(sdp) {
  peerConnection = new RTCPeerConnection({ iceServers: [] });

  peerConnection.addEventListener("icecandidate", (event) => {
    if (event.candidate) {
      send({
        type: "iceCandidate",
        candidate: event.candidate.candidate,
        sdpMid: event.candidate.sdpMid,
        sdpMLineIndex: event.candidate.sdpMLineIndex,
      });
    }
  });

  peerConnection.addEventListener("connectionstatechange", () => {
    console.log("[mirrordrop] connectionState:", peerConnection.connectionState);
    setStatus(rtcStatusLabel(peerConnection.connectionState));
  });

  peerConnection.addEventListener("datachannel", (event) => {
    dataChannel = event.channel;
    dataChannel.binaryType = "arraybuffer";
    dataChannel.addEventListener("open", () => {
      console.log("[mirrordrop] dataChannel open");
      setStatus("Connected via WebRTC");
      send({ type: "requestManifest" });
    });
    dataChannel.addEventListener("message", (event) => {
      if (event.data instanceof ArrayBuffer) {
        handleDataChannelFrame(event.data);
      }
    });
  });

  await peerConnection.setRemoteDescription({ type: "offer", sdp });
  const answer = await peerConnection.createAnswer();
  await peerConnection.setLocalDescription(answer);
  send({ type: "answer", sdp: answer.sdp });
}

function renderManifest(files) {
  contentEl.hidden = files.length === 0;
  contentEl.innerHTML = "";
  if (files.length === 0) return;

  const list = document.createElement("div");
  list.className = "file-list";
  files.forEach((file) => {
    const row = document.createElement("div");
    row.className = "file-row";
    row.id = `file-row-${file.id}`;

    const name = document.createElement("span");
    name.className = "file-name";
    name.textContent = file.name;

    const action = document.createElement("button");
    action.textContent = "Download";
    action.addEventListener("click", () => requestDownload(file.id, action));

    const progress = document.createElement("div");
    progress.className = "progress-row";
    progress.hidden = true;
    progress.innerHTML = '<div class="progress-bar"><div></div></div><span class="progress-label"></span>';

    row.appendChild(name);
    row.appendChild(action);
    row.appendChild(progress);
    list.appendChild(row);
  });
  contentEl.appendChild(list);
}

function requestDownload(fileId, buttonEl) {
  buttonEl.disabled = true;
  buttonEl.textContent = "Requesting…";
  send({ type: "requestFiles", fileIds: [fileId] });
}

function handleDataChannelFrame(buffer) {
  const view = new DataView(buffer);
  const tag = view.getUint8(0);
  const transferId = String(view.getUint32(1, false));

  if (tag === FRAME_TAG_FILE_START) {
    const json = JSON.parse(new TextDecoder().decode(buffer.slice(5)));
    activeTransfers.set(transferId, {
      fileId: json.fileId,
      name: json.name,
      mimeType: json.mimeType,
      size: json.size,
      sha256: json.sha256,
      chunks: [],
      received: 0,
    });
    setRowProgress(json.fileId, 0);
  } else if (tag === FRAME_TAG_FILE_CHUNK) {
    const transfer = activeTransfers.get(transferId);
    if (!transfer) return;
    const payload = buffer.slice(9);
    transfer.chunks.push(payload);
    transfer.received += payload.byteLength;
    setRowProgress(transfer.fileId, transfer.received / transfer.size);
  } else if (tag === FRAME_TAG_FILE_END) {
    const transfer = activeTransfers.get(transferId);
    activeTransfers.delete(transferId);
    if (!transfer) return;
    const json = JSON.parse(new TextDecoder().decode(buffer.slice(5)));
    finishTransfer(transfer, json);
  }
}

async function finishTransfer(transfer, endInfo) {
  const blob = new Blob(transfer.chunks, { type: transfer.mimeType });
  const bytes = new Uint8Array(await blob.arrayBuffer());

  if (bytes.byteLength !== endInfo.byteCount) {
    setRowStatus(transfer.fileId, `Size mismatch (got ${bytes.byteLength}, expected ${endInfo.byteCount})`, true);
    return;
  }

  const digest = await crypto.subtle.digest("SHA-256", bytes);
  const hex = bytesToHex(new Uint8Array(digest));
  if (hex !== transfer.sha256 || hex !== endInfo.sha256) {
    setRowStatus(transfer.fileId, "Checksum mismatch - file rejected", true);
    return;
  }

  downloadBlob(blob, transfer.name);
  setRowStatus(transfer.fileId, "Downloaded & verified ✓", false);
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}

function setRowProgress(fileId, fraction) {
  const row = document.getElementById(`file-row-${fileId}`);
  if (!row) return;
  const progressRow = row.querySelector(".progress-row");
  const bar = row.querySelector(".progress-bar > div");
  progressRow.hidden = false;
  bar.style.width = `${Math.min(100, Math.round(fraction * 100))}%`;
}

function setRowStatus(fileId, text, isError) {
  const row = document.getElementById(`file-row-${fileId}`);
  if (!row) return;
  const label = row.querySelector(".progress-label");
  if (label) {
    label.textContent = text;
    label.classList.toggle("error-text", !!isError);
  }
  const button = row.querySelector("button");
  if (button) {
    button.disabled = false;
    button.textContent = "Download again";
  }
}

function bytesToHex(bytes) {
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function rtcStatusLabel(state) {
  switch (state) {
    case "connecting":
      return "Connecting…";
    case "connected":
      return "Connected via WebRTC";
    case "disconnected":
      return "Connection lost - reconnecting…";
    case "failed":
      return "Connection failed";
    case "closed":
      return "Disconnected";
    default:
      return "Connected to MirrorDash";
  }
}

function guessDeviceName() {
  const ua = navigator.userAgent || "";
  if (/iPhone/.test(ua)) return "iPhone";
  if (/iPad/.test(ua)) return "iPad";
  if (/Android/.test(ua)) return "Android device";
  if (/Macintosh/.test(ua)) return "Mac";
  if (/Windows/.test(ua)) return "Windows PC";
  return "Guest";
}

connectSignaling();
