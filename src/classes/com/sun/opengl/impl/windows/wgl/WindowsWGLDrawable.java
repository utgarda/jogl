/*
 * Copyright (c) 2003 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * You acknowledge that this software is not designed or intended for use
 * in the design, construction, operation or maintenance of any nuclear
 * facility.
 * 
 * Sun gratefully acknowledges that this software was originally authored
 * and developed by Kenneth Bradley Russell and Christopher John Kline.
 */

package com.sun.opengl.impl.windows.wgl;

import javax.media.nwi.*;
import javax.media.opengl.*;
import com.sun.opengl.impl.*;

public abstract class WindowsWGLDrawable extends GLDrawableImpl {
  protected static final boolean DEBUG = Debug.debug("WindowsWGLDrawable");

  protected NWCapabilitiesChooser chooser;
  protected boolean pixelFormatChosen;

  // Workaround for problems on Intel 82855 cards
  private int  setPixelFormatFailCount;
  private static final int MAX_SET_PIXEL_FORMAT_FAIL_COUNT = 5;

  protected static final int MAX_PFORMATS = 256;
  protected static final int MAX_ATTRIBS  = 256;

  public WindowsWGLDrawable(GLDrawableFactory factory, NativeWindow comp, boolean realized,
                            NWCapabilities requestedCapabilities,
                            NWCapabilitiesChooser chooser) {
    super(factory, comp, requestedCapabilities, realized);
    this.chooser = chooser;
  }

  public int lockSurface() throws GLException {
    int ret = super.lockSurface();
    if(NativeWindow.LOCK_SURFACE_NOT_READY == ret) {
      if (DEBUG) {
          System.err.println("WindowsWGLDrawable.lockSurface: surface not ready");
      }
      return ret;
    }
    if (!pixelFormatChosen) {
      try {
        choosePixelFormat(true);
        setPixelFormatFailCount = 0;
      } catch (RuntimeException e) {
        if (DEBUG) {
          System.err.println("WindowsWGLDrawable.lockSurface: squelching exception");
          e.printStackTrace();
        }
        // Workaround for problems seen on Intel 82855 cards in particular
        // Make it look like the lockSurface() call didn't succeed
        unlockSurface();
        if (e instanceof GLException) {
          if (++setPixelFormatFailCount == MAX_SET_PIXEL_FORMAT_FAIL_COUNT) {
            setPixelFormatFailCount = 0;
            throw e;
          }
          return NativeWindow.LOCK_SURFACE_NOT_READY;
        } else {
          // Probably a user error in the NWCapabilitiesChooser or similar.
          // Don't propagate non-GLExceptions out because calling code
          // expects to catch only that exception type
          throw new GLException(e);
        }
      }
    }
    return ret;
  }


  protected void choosePixelFormat(boolean onscreen) {
    PIXELFORMATDESCRIPTOR pfd = null;
    int pixelFormat = 0;
    NWCapabilities chosenCaps = null;
    NWCapabilities capabilities = getRequestedNWCapabilities();
    long hdc = getNativeWindow().getSurfaceHandle();
    if (onscreen) {
      if ((pixelFormat = WGL.GetPixelFormat(hdc)) != 0) {
        // The Java2D/OpenGL pipeline probably already set a pixel
        // format for this canvas.
        if (DEBUG) {
          System.err.println("NOTE: pixel format already chosen (by Java2D/OpenGL pipeline?) for window: " + 
                             WGL.GetPixelFormat(hdc));
        }
        pfd = newPixelFormatDescriptor();
        if (WGL.DescribePixelFormat(hdc, pixelFormat, pfd.size(), pfd) == 0) {
          // FIXME: should this just be a warning? Not really critical...
          throw new GLException("Unable to describe pixel format " + pixelFormat +
                                " of window set by Java2D/OpenGL pipeline");
        }
        setChosenNWCapabilities(pfd2NWCapabilities(pfd));
        pixelFormatChosen = true;
        return;
      }

      NWCapabilities[] availableCaps = null;
      int numFormats = 0;
      pfd = newPixelFormatDescriptor();
      // Produce a recommended pixel format selection for the NWCapabilitiesChooser.
      // Use wglChoosePixelFormatARB if user requested multisampling and if we have it available
      WindowsWGLDrawable dummyDrawable = null;
      GLContextImpl     dummyContext  = null;
      WGLExt            dummyWGLExt   = null;
      if (capabilities.getSampleBuffers()) {
        dummyDrawable = new WindowsDummyWGLDrawable(getFactory());
        dummyContext  = (GLContextImpl) dummyDrawable.createContext(null);
        if (dummyContext != null) {
          dummyContext.makeCurrent();
          dummyWGLExt = (WGLExt) dummyContext.getPlatformGLExtensions();
        }
      }      
      int recommendedPixelFormat = -1;
      boolean haveWGLChoosePixelFormatARB = false;
      boolean haveWGLARBMultisample = false;
      boolean gotAvailableCaps = false;
      if (dummyWGLExt != null) {
        try {
          haveWGLChoosePixelFormatARB = dummyWGLExt.isExtensionAvailable("WGL_ARB_pixel_format");
          if (haveWGLChoosePixelFormatARB) {
            haveWGLARBMultisample = dummyWGLExt.isExtensionAvailable("WGL_ARB_multisample");

            int[]   iattributes = new int  [2 * MAX_ATTRIBS];
            int[]   iresults    = new int  [2 * MAX_ATTRIBS];
            float[] fattributes = new float[1];

            if (glCapabilities2iattributes(capabilities,
                                           iattributes,
                                           dummyWGLExt,
                                           false,
                                           null)) {
              int[] pformats = new int[MAX_PFORMATS];
              int[] numFormatsTmp = new int[1];
              if (dummyWGLExt.wglChoosePixelFormat(hdc,
                                                   iattributes, 0,
                                                   fattributes, 0,
                                                   MAX_PFORMATS,
                                                   pformats, 0,
                                                   numFormatsTmp, 0)) {
                numFormats = numFormatsTmp[0];
                if (numFormats > 0) {
                  // Remove one-basing of pixel format (added on later)
                  recommendedPixelFormat = pformats[0] - 1;
                  if (DEBUG) {
                    System.err.println(getThreadName() + ": Used wglChoosePixelFormatARB to recommend pixel format " + recommendedPixelFormat);
                  }
                }
              } else {
                if (DEBUG) {
                  System.err.println(getThreadName() + ": wglChoosePixelFormatARB failed: " + WGL.GetLastError() );
                  Thread.dumpStack();
                }
              }
              if (DEBUG) {
                if (recommendedPixelFormat < 0) {
                  System.err.print(getThreadName() + ": wglChoosePixelFormatARB didn't recommend a pixel format");
                  if (capabilities.getSampleBuffers()) {
                    System.err.print(" for multisampled NWCapabilities");
                  }
                  System.err.println();
                }
              }

              // Produce a list of NWCapabilities to give to the
              // NWCapabilitiesChooser.
              // Use wglGetPixelFormatAttribivARB instead of
              // DescribePixelFormat to get higher-precision information
              // about the pixel format (should make the NWCapabilities
              // more precise as well...i.e., remove the
              // "HardwareAccelerated" bit, which is basically
              // meaningless, and put in whether it can render to a
              // window, to a pbuffer, or to a pixmap)
              int niattribs = 0;
              iattributes[0] = WGLExt.WGL_NUMBER_PIXEL_FORMATS;
              if (dummyWGLExt.wglGetPixelFormatAttribiv(hdc, 0, 0, 1, iattributes, 0, iresults, 0)) {
                numFormats = iresults[0];

                if (DEBUG) {
                  System.err.println("wglGetPixelFormatAttribivARB reported WGL_NUMBER_PIXEL_FORMATS = " + numFormats);
                }

                // Should we be filtering out the pixel formats which aren't
                // applicable, as we are doing here?
                // We don't have enough information in the NWCapabilities to
                // represent those that aren't...
                iattributes[niattribs++] = WGLExt.WGL_DRAW_TO_WINDOW;
                iattributes[niattribs++] = WGLExt.WGL_ACCELERATION;
                iattributes[niattribs++] = WGLExt.WGL_SUPPORT_OPENGL;
                iattributes[niattribs++] = WGLExt.WGL_DEPTH_BITS;
                iattributes[niattribs++] = WGLExt.WGL_STENCIL_BITS;
                iattributes[niattribs++] = WGLExt.WGL_DOUBLE_BUFFER;
                iattributes[niattribs++] = WGLExt.WGL_STEREO;
                iattributes[niattribs++] = WGLExt.WGL_PIXEL_TYPE;
                iattributes[niattribs++] = WGLExt.WGL_RED_BITS;
                iattributes[niattribs++] = WGLExt.WGL_GREEN_BITS;
                iattributes[niattribs++] = WGLExt.WGL_BLUE_BITS;
                iattributes[niattribs++] = WGLExt.WGL_ALPHA_BITS;
                iattributes[niattribs++] = WGLExt.WGL_ACCUM_RED_BITS;
                iattributes[niattribs++] = WGLExt.WGL_ACCUM_GREEN_BITS;
                iattributes[niattribs++] = WGLExt.WGL_ACCUM_BLUE_BITS;
                iattributes[niattribs++] = WGLExt.WGL_ACCUM_ALPHA_BITS;
                if (haveWGLARBMultisample) {
                  iattributes[niattribs++] = WGLExt.WGL_SAMPLE_BUFFERS;
                  iattributes[niattribs++] = WGLExt.WGL_SAMPLES;
                }

                availableCaps = new NWCapabilities[numFormats];
                for (int i = 0; i < numFormats; i++) {
                  if (!dummyWGLExt.wglGetPixelFormatAttribiv(hdc, i+1, 0, niattribs, iattributes, 0, iresults, 0)) {
                    throw new GLException("Error getting pixel format attributes for pixel format " + (i + 1) + " of device context");
                  }
                  availableCaps[i] = iattributes2NWCapabilities(iattributes, niattribs, iresults, true);
                }
                gotAvailableCaps = true;
              } else {
                long lastErr = WGL.GetLastError();
                // Intel Extreme graphics fails with a zero error code
                if (lastErr != 0) {
                  throw new GLException("Unable to enumerate pixel formats of window using wglGetPixelFormatAttribivARB: error code " + WGL.GetLastError());
                }
              }
            }
          }
        } finally {
          dummyContext.release();
          dummyContext.destroy();
          dummyDrawable.destroy();
        }
      }

      // Fallback path for older cards, in particular Intel Extreme motherboard graphics
      if (!gotAvailableCaps) {
        if (DEBUG) {
          if (!capabilities.getSampleBuffers()) {
            System.err.println(getThreadName() + ": Using ChoosePixelFormat because multisampling not requested");
          } else {
            System.err.println(getThreadName() + ": Using ChoosePixelFormat because no wglChoosePixelFormatARB");
          }
        }
        pfd = glCapabilities2PFD(capabilities, onscreen);
        // Remove one-basing of pixel format (added on later)
        recommendedPixelFormat = WGL.ChoosePixelFormat(hdc, pfd) - 1;
        if (DEBUG) {
          System.err.println(getThreadName() + ": Recommended pixel format = " + recommendedPixelFormat);
        }

        numFormats = WGL.DescribePixelFormat(hdc, 1, 0, null);
        if (numFormats == 0) {
          throw new GLException("Unable to enumerate pixel formats of window " +
                                toHexString(hdc) + " for NWCapabilitiesChooser");
        }
        availableCaps = new NWCapabilities[numFormats];
        for (int i = 0; i < numFormats; i++) {
          if (WGL.DescribePixelFormat(hdc, 1 + i, pfd.size(), pfd) == 0) {
            throw new GLException("Error describing pixel format " + (1 + i) + " of device context");
          }
          availableCaps[i] = pfd2NWCapabilities(pfd);
        }
      }

      // NOTE: officially, should make a copy of all of these
      // NWCapabilities to avoid mutation by the end user during the
      // chooseCapabilities call, but for the time being, assume they
      // won't be changed

      // Supply information to chooser
      pixelFormat = chooser.chooseCapabilities(capabilities, availableCaps, recommendedPixelFormat);
      if ((pixelFormat < 0) || (pixelFormat >= numFormats)) {
        throw new GLException("Invalid result " + pixelFormat +
                              " from NWCapabilitiesChooser (should be between 0 and " +
                              (numFormats - 1) + ")");
      }
      if (DEBUG) {
        System.err.println(getThreadName() + ": Chosen pixel format (" + pixelFormat + "):");
        System.err.println(availableCaps[pixelFormat]);
      }
      chosenCaps = availableCaps[pixelFormat];
      pixelFormat += 1; // one-base the index
      if (WGL.DescribePixelFormat(hdc, pixelFormat, pfd.size(), pfd) == 0) {
        throw new GLException("Error re-describing the chosen pixel format: " + WGL.GetLastError());
      }
    } else {
      // For now, use ChoosePixelFormat for offscreen surfaces until
      // we figure out how to properly choose an offscreen-
      // compatible pixel format
      pfd = glCapabilities2PFD(capabilities, onscreen);
      pixelFormat = WGL.ChoosePixelFormat(hdc, pfd);
    }
    if (!WGL.SetPixelFormat(hdc, pixelFormat, pfd)) {
      long lastError = WGL.GetLastError();
      if (DEBUG) {
        System.err.println(getThreadName() + ": SetPixelFormat failed: current context = " + WGL.wglGetCurrentContext() +
                           ", current DC = " + WGL.wglGetCurrentDC());
        System.err.println(getThreadName() + ": GetPixelFormat(hdc " + toHexString(hdc) + ") returns " + WGL.GetPixelFormat(hdc));
      }
      throw new GLException("Unable to set pixel format " + pixelFormat + " for device context " + toHexString(hdc) + ": error code " + lastError);
    }
    // Reuse the previously-constructed NWCapabilities because it
    // turns out that using DescribePixelFormat on some pixel formats
    // (which, for example, support full-scene antialiasing) for some
    // reason return that they are not OpenGL-capable
    if (chosenCaps != null) {
      setChosenNWCapabilities(chosenCaps);
    } else {
      setChosenNWCapabilities(pfd2NWCapabilities(pfd));
    }
    pixelFormatChosen = true;
  }

  protected static PIXELFORMATDESCRIPTOR glCapabilities2PFD(NWCapabilities caps, boolean onscreen) {
    int colorDepth = (caps.getRedBits() +
                      caps.getGreenBits() +
                      caps.getBlueBits());
    if (colorDepth < 15) {
      throw new GLException("Bit depths < 15 (i.e., non-true-color) not supported");
    }
    PIXELFORMATDESCRIPTOR pfd = newPixelFormatDescriptor();
    int pfdFlags = (WGL.PFD_SUPPORT_OPENGL |
                    WGL.PFD_GENERIC_ACCELERATED);
    if (caps.getDoubleBuffered()) {
      pfdFlags |= WGL.PFD_DOUBLEBUFFER;
    }
    if (onscreen) {
      pfdFlags |= WGL.PFD_DRAW_TO_WINDOW;
    } else {
      pfdFlags |= WGL.PFD_DRAW_TO_BITMAP;
    }
    if (caps.getStereo()) {
      pfdFlags |= WGL.PFD_STEREO;
    }
    pfd.dwFlags(pfdFlags);
    pfd.iPixelType((byte) WGL.PFD_TYPE_RGBA);
    pfd.cColorBits((byte) colorDepth);
    pfd.cRedBits  ((byte) caps.getRedBits());
    pfd.cGreenBits((byte) caps.getGreenBits());
    pfd.cBlueBits ((byte) caps.getBlueBits());
    pfd.cAlphaBits((byte) caps.getAlphaBits());
    int accumDepth = (caps.getAccumRedBits() +
                      caps.getAccumGreenBits() +
                      caps.getAccumBlueBits());
    pfd.cAccumBits     ((byte) accumDepth);
    pfd.cAccumRedBits  ((byte) caps.getAccumRedBits());
    pfd.cAccumGreenBits((byte) caps.getAccumGreenBits());
    pfd.cAccumBlueBits ((byte) caps.getAccumBlueBits());
    pfd.cAccumAlphaBits((byte) caps.getAccumAlphaBits());
    pfd.cDepthBits((byte) caps.getDepthBits());
    pfd.cStencilBits((byte) caps.getStencilBits());
    pfd.iLayerType((byte) WGL.PFD_MAIN_PLANE);
    return pfd;
  }

  protected static PIXELFORMATDESCRIPTOR newPixelFormatDescriptor() {
    PIXELFORMATDESCRIPTOR pfd = PIXELFORMATDESCRIPTOR.create();
    pfd.nSize((short) pfd.size());
    pfd.nVersion((short) 1);
    return pfd;
  }

  protected static NWCapabilities pfd2NWCapabilities(PIXELFORMATDESCRIPTOR pfd) {
    if ((pfd.dwFlags() & WGL.PFD_SUPPORT_OPENGL) == 0) {
      return null;
    }
    NWCapabilities res = new NWCapabilities();
    res.setRedBits       (pfd.cRedBits());
    res.setGreenBits     (pfd.cGreenBits());
    res.setBlueBits      (pfd.cBlueBits());
    res.setAlphaBits     (pfd.cAlphaBits());
    res.setAccumRedBits  (pfd.cAccumRedBits());
    res.setAccumGreenBits(pfd.cAccumGreenBits());
    res.setAccumBlueBits (pfd.cAccumBlueBits());
    res.setAccumAlphaBits(pfd.cAccumAlphaBits());
    res.setDepthBits     (pfd.cDepthBits());
    res.setStencilBits   (pfd.cStencilBits());
    res.setDoubleBuffered((pfd.dwFlags() & WGL.PFD_DOUBLEBUFFER) != 0);
    res.setStereo        ((pfd.dwFlags() & WGL.PFD_STEREO) != 0);
    res.setHardwareAccelerated(((pfd.dwFlags() & WGL.PFD_GENERIC_FORMAT) == 0) ||
			       ((pfd.dwFlags() & WGL.PFD_GENERIC_ACCELERATED) != 0));
    return res;
  }

  protected static boolean glCapabilities2iattributes(NWCapabilities capabilities,
                                                      int[] iattributes,
                                                      WGLExt wglExt,
                                                      boolean pbuffer,
                                                      int[] floatMode) throws GLException {
    if (!wglExt.isExtensionAvailable("WGL_ARB_pixel_format")) {
      return false;
    }

    int niattribs = 0;

    iattributes[niattribs++] = WGLExt.WGL_SUPPORT_OPENGL;
    iattributes[niattribs++] = GL.GL_TRUE;
    if (pbuffer) {
      iattributes[niattribs++] = WGLExt.WGL_DRAW_TO_PBUFFER;
      iattributes[niattribs++] = GL.GL_TRUE;
    } else {
      iattributes[niattribs++] = WGLExt.WGL_DRAW_TO_WINDOW;
      iattributes[niattribs++] = GL.GL_TRUE;
    }

    iattributes[niattribs++] = WGLExt.WGL_DOUBLE_BUFFER;
    if (capabilities.getDoubleBuffered()) {
      iattributes[niattribs++] = GL.GL_TRUE;
    } else {
      iattributes[niattribs++] = GL.GL_FALSE;
    }

    iattributes[niattribs++] = WGLExt.WGL_STEREO;
    if (capabilities.getStereo()) {
      iattributes[niattribs++] = GL.GL_TRUE;
    } else {
      iattributes[niattribs++] = GL.GL_FALSE;
    }
    
    iattributes[niattribs++] = WGLExt.WGL_DEPTH_BITS;
    iattributes[niattribs++] = capabilities.getDepthBits();
    iattributes[niattribs++] = WGLExt.WGL_RED_BITS;
    iattributes[niattribs++] = capabilities.getRedBits();
    iattributes[niattribs++] = WGLExt.WGL_GREEN_BITS;
    iattributes[niattribs++] = capabilities.getGreenBits();
    iattributes[niattribs++] = WGLExt.WGL_BLUE_BITS;
    iattributes[niattribs++] = capabilities.getBlueBits();
    iattributes[niattribs++] = WGLExt.WGL_ALPHA_BITS;
    iattributes[niattribs++] = capabilities.getAlphaBits();
    iattributes[niattribs++] = WGLExt.WGL_STENCIL_BITS;
    iattributes[niattribs++] = capabilities.getStencilBits();
    if (capabilities.getAccumRedBits()   > 0 ||
        capabilities.getAccumGreenBits() > 0 ||
        capabilities.getAccumBlueBits()  > 0 ||
        capabilities.getAccumAlphaBits() > 0) {
      iattributes[niattribs++] = WGLExt.WGL_ACCUM_BITS;
      iattributes[niattribs++] = (capabilities.getAccumRedBits() +
                                  capabilities.getAccumGreenBits() +
                                  capabilities.getAccumBlueBits() +
                                  capabilities.getAccumAlphaBits());
      iattributes[niattribs++] = WGLExt.WGL_ACCUM_RED_BITS;
      iattributes[niattribs++] = capabilities.getAccumRedBits();
      iattributes[niattribs++] = WGLExt.WGL_ACCUM_GREEN_BITS;
      iattributes[niattribs++] = capabilities.getAccumGreenBits();
      iattributes[niattribs++] = WGLExt.WGL_ACCUM_BLUE_BITS;
      iattributes[niattribs++] = capabilities.getAccumBlueBits();
      iattributes[niattribs++] = WGLExt.WGL_ACCUM_ALPHA_BITS;
      iattributes[niattribs++] = capabilities.getAccumAlphaBits();
    }

    if (wglExt.isExtensionAvailable("WGL_ARB_multisample")) {
      if (capabilities.getSampleBuffers()) {
        iattributes[niattribs++] = WGLExt.WGL_SAMPLE_BUFFERS;
        iattributes[niattribs++] = GL.GL_TRUE;
        iattributes[niattribs++] = WGLExt.WGL_SAMPLES;
        iattributes[niattribs++] = capabilities.getNumSamples();
      }
    }

    boolean rtt      = capabilities.getPbufferRenderToTexture();
    boolean rect     = capabilities.getPbufferRenderToTextureRectangle();
    boolean useFloat = capabilities.getPbufferFloatingPointBuffers();
    boolean ati      = false;
    if (pbuffer) {
      // Check some invariants and set up some state
      if (rect && !rtt) {
        throw new GLException("Render-to-texture-rectangle requires render-to-texture to be specified");
      }

      if (rect) {
        if (!wglExt.isExtensionAvailable("GL_NV_texture_rectangle")) {
          throw new GLException("Render-to-texture-rectangle requires GL_NV_texture_rectangle extension");
        }
      }

      if (useFloat) {
        if (!wglExt.isExtensionAvailable("WGL_ATI_pixel_format_float") &&
            !wglExt.isExtensionAvailable("WGL_NV_float_buffer")) {
          throw new GLException("Floating-point pbuffers not supported by this hardware");
        }

        // Prefer NVidia extension over ATI
        if (wglExt.isExtensionAvailable("WGL_NV_float_buffer")) {
          ati = false;
          floatMode[0] = GLPbuffer.NV_FLOAT;
        } else {
          ati = true;
          floatMode[0] = GLPbuffer.ATI_FLOAT;
        }
        if (DEBUG) {
          System.err.println("Using " + (ati ? "ATI" : "NVidia") + " floating-point extension");
        }
      }

      // See whether we need to change the pixel type to support ATI's
      // floating-point pbuffers
      if (useFloat && ati) {
        if (rtt) {
          throw new GLException("Render-to-floating-point-texture not supported on ATI hardware");
        } else {
          iattributes[niattribs++] = WGLExt.WGL_PIXEL_TYPE;
          iattributes[niattribs++] = WGLExt.WGL_TYPE_RGBA_FLOAT;
        }
      } else {
        if (!rtt) {
          // Currently we don't support non-truecolor visuals in the
          // NWCapabilities, so we don't offer the option of making
          // color-index pbuffers.
          iattributes[niattribs++] = WGLExt.WGL_PIXEL_TYPE;
          iattributes[niattribs++] = WGLExt.WGL_TYPE_RGBA;
        }
      }

      if (useFloat && !ati) {
        iattributes[niattribs++] = WGLExt.WGL_FLOAT_COMPONENTS_NV;
        iattributes[niattribs++] = GL.GL_TRUE;
      }

      if (rtt) {
        if (useFloat) {
          assert(!ati);
          if (!rect) {
            throw new GLException("Render-to-floating-point-texture only supported on NVidia hardware with render-to-texture-rectangle");
          }
          iattributes[niattribs++] = WGLExt.WGL_BIND_TO_TEXTURE_RECTANGLE_FLOAT_RGB_NV;
          iattributes[niattribs++] = GL.GL_TRUE;
        } else {
          iattributes[niattribs++] = rect ? WGLExt.WGL_BIND_TO_TEXTURE_RECTANGLE_RGB_NV : WGLExt.WGL_BIND_TO_TEXTURE_RGB;
          iattributes[niattribs++] = GL.GL_TRUE;
        }
      }
    } else {
      iattributes[niattribs++] = WGLExt.WGL_PIXEL_TYPE;
      iattributes[niattribs++] = WGLExt.WGL_TYPE_RGBA;
    }

    return true;
  }

  protected static NWCapabilities iattributes2NWCapabilities(int[] iattribs,
                                                             int niattribs,
                                                             int[] iresults,
                                                             boolean requireRenderToWindow) {
    NWCapabilities res = new NWCapabilities();
    for (int i = 0; i < niattribs; i++) {
      int attr = iattribs[i];
      switch (attr) {
        case WGLExt.WGL_DRAW_TO_WINDOW:
          if (requireRenderToWindow && iresults[i] != GL.GL_TRUE)
            return null;
          break;

        case WGLExt.WGL_DRAW_TO_PBUFFER:
          break;

        case WGLExt.WGL_ACCELERATION:
          res.setHardwareAccelerated(iresults[i] == WGLExt.WGL_FULL_ACCELERATION);
          break;

        case WGLExt.WGL_SUPPORT_OPENGL:
          if (iresults[i] != GL.GL_TRUE)
            return null;
          break;

        case WGLExt.WGL_DEPTH_BITS:
          res.setDepthBits(iresults[i]);
          break;

        case WGLExt.WGL_STENCIL_BITS:
          res.setStencilBits(iresults[i]);
          break;

        case WGLExt.WGL_DOUBLE_BUFFER:
          res.setDoubleBuffered(iresults[i] == GL.GL_TRUE);
          break;

        case WGLExt.WGL_STEREO:
          res.setStereo(iresults[i] == GL.GL_TRUE);
          break;

        case WGLExt.WGL_PIXEL_TYPE:
          // Fail softly with unknown results here
          if (iresults[i] == WGLExt.WGL_TYPE_RGBA||
              iresults[i] == WGLExt.WGL_TYPE_RGBA_FLOAT) {
            res.setPbufferFloatingPointBuffers(true);
          }
          break;

        case WGLExt.WGL_FLOAT_COMPONENTS_NV:
          if (iresults[i] != 0) {
            res.setPbufferFloatingPointBuffers(true);
          }
          break;

        case WGLExt.WGL_RED_BITS:
          res.setRedBits(iresults[i]);
          break;
          
        case WGLExt.WGL_GREEN_BITS:
          res.setGreenBits(iresults[i]);
          break;

        case WGLExt.WGL_BLUE_BITS:
          res.setBlueBits(iresults[i]);
          break;

        case WGLExt.WGL_ALPHA_BITS:
          res.setAlphaBits(iresults[i]);
          break;

        case WGLExt.WGL_ACCUM_RED_BITS:
          res.setAccumRedBits(iresults[i]);
          break;

        case WGLExt.WGL_ACCUM_GREEN_BITS:
          res.setAccumGreenBits(iresults[i]);
          break;

        case WGLExt.WGL_ACCUM_BLUE_BITS:
          res.setAccumBlueBits(iresults[i]);
          break;

        case WGLExt.WGL_ACCUM_ALPHA_BITS:
          res.setAccumAlphaBits(iresults[i]);
          break;

        case WGLExt.WGL_SAMPLE_BUFFERS:
          res.setSampleBuffers(iresults[i] != 0);
          break;

        case WGLExt.WGL_SAMPLES:
          res.setNumSamples(iresults[i]);
          break;

        default:
          throw new GLException("Unknown pixel format attribute " + iattribs[i]);
      }
    }
    return res;
  }

  protected static String getThreadName() {
    return Thread.currentThread().getName();
  }
}