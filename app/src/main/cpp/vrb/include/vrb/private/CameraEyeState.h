/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRB_CAMERA_EYE_STATE_DOT_H
#define VRB_CAMERA_EYE_STATE_DOT_H

#include "vrb/CameraEye.h"

#include "vrb/Matrix.h"

namespace vrb {

struct CameraEye::State {
  bool dirty;
  Matrix headTransform;
  Matrix eyeTransform;
  Matrix perspective;

  // calculated from headTransform * eyeTransform
  Matrix transform;
  // calculated from (headTransform * eyeTransform).Inverse()
  Matrix view;

  State();
  void Update();
};

} // namespace vrb

#endif // VRB_CAMERA_EYE_STATE_DOT_H
