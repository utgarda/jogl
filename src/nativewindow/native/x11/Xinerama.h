/*
Copyright (C) 1994-2001 The XFree86 Project, Inc. All Rights Reserved.

Permission is hereby granted, free of charge, to any person obtaining a
copy of this software and associated documentation files (the "Soft-
ware"), to deal in the Software without restriction, including without
limitation the rights to use, copy, modify, merge, publish, distribute,
and/or sell copies of the Software, and to permit persons to whom the
Software is furnished to do so, provided that the above copyright
notice(s) and this permission notice appear in all copies of the Soft-
ware and that both the above copyright notice(s) and this permission
notice appear in supporting documentation.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABIL-
ITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF THIRD PARTY
RIGHTS. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN
THIS NOTICE BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSE-
QUENTIAL DAMAGES, OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFOR-
MANCE OF THIS SOFTWARE.

Except as contained in this notice, the name of a copyright holder shall
not be used in advertising or otherwise to promote the sale, use or
other dealings in this Software without prior written authorization of
the copyright holder.
*/

/* $XFree86: xc/include/extensions/Xinerama.h,v 3.2 2000/03/01 01:04:20 dawes Exp $ */

#ifdef __linux__

#ifndef _Xinerama_h
#define _Xinerama_h

typedef struct {
   int   screen_number;
   short x_org;
   short y_org;
   short width;
   short height;
} XineramaScreenInfo;

Bool XineramaQueryExtension (
   Display *dpy,
   int     *event_base,
   int     *error_base
);

Status XineramaQueryVersion(
   Display *dpy,
   int     *major,
   int     *minor
);

Bool XineramaIsActive(Display *dpy);


/* 
   Returns the number of heads and a pointer to an array of
   structures describing the position and size of the individual
   heads.  Returns NULL and number = 0 if Xinerama is not active.
  
   Returned array should be freed with XFree().
*/

XineramaScreenInfo * 
XineramaQueryScreens(
   Display *dpy,
   int     *number
);

#endif /* _Xinerama_h */

#endif /* __linux__ */
