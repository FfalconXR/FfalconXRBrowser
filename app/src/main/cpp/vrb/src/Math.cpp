/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "vrb/Color.h"
#include "vrb/Vector.h"

#include <limits>

namespace vrb {

const Color&
Color::Zero() {
  static const Color result;
  return result;
}

const Vector&
Vector::Zero() {
  static const Vector result;
  return result;
}

const Vector&
Vector::Min() {
  static const Vector result(std::numeric_limits<float>::min(), std::numeric_limits<float>::min(), std::numeric_limits<float>::min());
  return result;
}

const Vector&
Vector::Max() {
  static const Vector result(std::numeric_limits<float>::max(), std::numeric_limits<float>::max(), std::numeric_limits<float>::max());
  return result;
}

}
