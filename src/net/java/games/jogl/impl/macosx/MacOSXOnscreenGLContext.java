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

package net.java.games.jogl.impl.macosx;

import java.util.*;

import net.java.games.jogl.*;
import net.java.games.jogl.impl.*;

public class MacOSXOnscreenGLContext extends MacOSXGLContext {
  protected MacOSXOnscreenGLDrawable drawable;
  // Variables for pbuffer support
  List pbuffersToInstantiate = new ArrayList();

  public MacOSXOnscreenGLContext(MacOSXOnscreenGLDrawable drawable,
                                 GLContext shareWith) {
    super(drawable, shareWith);
    this.drawable = drawable;
  }

  public boolean canCreatePbufferContext() {
    return true;
  }
    
  public GLDrawableImpl createPbufferDrawable(GLCapabilities capabilities,
                                              int initialWidth,
                                              int initialHeight) {
    MacOSXPbufferGLDrawable buf = new MacOSXPbufferGLDrawable(capabilities, initialWidth, initialHeight);
    pbuffersToInstantiate.add(buf);
    return buf;
  }
    
  protected int makeCurrentImpl() throws GLException {
    try {
      int lockRes = drawable.lockSurface();
      if (lockRes == MacOSXOnscreenGLDrawable.LOCK_SURFACE_NOT_READY) {
        return CONTEXT_NOT_CURRENT;
      }
      if (lockRes == MacOSXOnscreenGLDrawable.LOCK_SURFACE_CHANGED) {
        super.destroy();
      }
      int ret = super.makeCurrentImpl();
      if ((ret == CONTEXT_CURRENT) ||
          (ret == CONTEXT_CURRENT_NEW)) {
        // Assume the canvas might have been resized or moved and tell the OpenGL
        // context to update itself. This used to be done only upon receiving a
        // reshape event but that doesn't appear to be sufficient. An experiment
        // was also done to add a HierarchyBoundsListener to the GLCanvas and
        // do this updating only upon reshape of this component or reshape or movement
        // of an ancestor, but this also wasn't sufficient and left garbage on the
        // screen in some situations.
        CGL.updateContext(nsContext, drawable.getView());
        // Instantiate any pending pbuffers
        while (!pbuffersToInstantiate.isEmpty()) {
          MacOSXPbufferGLDrawable buf =
            (MacOSXPbufferGLDrawable) pbuffersToInstantiate.remove(pbuffersToInstantiate.size() - 1);
          buf.createPbuffer(getGL());
        }
      } else {
        // View might not have been ready
        drawable.unlockSurface();
      }
      return ret;
    } catch (RuntimeException e) {
      try {
        drawable.unlockSurface();
      } catch (Exception e2) {
        // do nothing if unlockSurface throws
      }
      throw(e); 
    }
  }
    
  protected void releaseImpl() throws GLException {
    try {
      super.releaseImpl();
    } finally {
      drawable.unlockSurface();
    }
  }
    
  public void swapBuffers() throws GLException {
    if (!CGL.flushBuffer(nsContext, drawable.getView())) {
      throw new GLException("Error swapping buffers");
    }
  }
}
