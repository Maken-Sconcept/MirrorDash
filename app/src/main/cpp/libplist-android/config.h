/* Hand-written replacement for libplist's autotools-generated config.h.
 * libplist has no external dependencies beyond a handful of standard C99/
 * POSIX headers and functions, all of which Android's Bionic libc provides
 * from API 21+ (well below this project's NDK floor), so this just asserts
 * that fact instead of running autoconf's ./configure. */
#ifndef LIBPLIST_CONFIG_H
#define LIBPLIST_CONFIG_H

#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1

#define HAVE_STRDUP 1
#define HAVE_STRNDUP 1
#define HAVE_STRERROR 1
#define HAVE_GMTIME_R 1
#define HAVE_LOCALTIME_R 1
#define HAVE_TIMEGM 1
#define HAVE_STRPTIME 1
#define HAVE_MEMMEM 1

#define PACKAGE_VERSION "2.3.0"

#endif
