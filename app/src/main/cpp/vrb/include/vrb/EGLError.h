/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRB_EGL_ERROR_DOT_H
#define VRB_EGL_ERROR_DOT_H

#include "vrb/gl.h"
#include <android/log.h>

namespace vrb {

const char* EGLErrorString();
const char* EGLErrorString(GLenum aError);
const char* EGLErrorCheck();

#define VRB_EGL_CHECK(X) X;                                 \
{                                                           \
  const char* str = vrb::EGLErrorCheck();                   \
  if (str) {                                                \
    __android_log_print(ANDROID_LOG_ERROR, "VRB",           \
                         "EGL Error: %s at%s:%s:%d",        \
                         str,                               \
                         __FILE__, __FUNCTION__, __LINE__); \
  }                                                         \
}

} // namespace vrb

#endif //  VRB_EGL_ERROR_DOT_H
