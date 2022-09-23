/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRB_MATRIX_DOT_H
#define VRB_MATRIX_DOT_H

#include "vrb/Vector.h"
#include "vrb/Quaternion.h"
#include "vrb/Logger.h"

#include <cfloat>
#include <cmath>
#include <cstdlib>

#define VRB_IS_ZERO(value) (std::fabs(value) < FLT_EPSILON)

namespace vrb {

const float PI_FLOAT = float(M_PI);
const float ToRadians = PI_FLOAT / 180.0f;
const float ToDegrees = 180.0f / PI_FLOAT;

class Matrix {
public:
  static Matrix Identity() {
    return Matrix(
      1.0f, 0.0f, 0.0f, 0.0f,
      0.0f, 1.0f, 0.0f, 0.0f,
      0.0f, 0.0f, 1.0f, 0.0f,
      0.0f, 0.0f, 0.0f, 1.0f);
  }

  static Matrix FromColumnMajor(const float aData[4][4]) {
    return Matrix(
        aData[0][0], aData[0][1], aData[0][2], aData[0][3],
        aData[1][0], aData[1][1], aData[1][2], aData[1][3],
        aData[2][0], aData[2][1], aData[2][2], aData[2][3],
        aData[3][0], aData[3][1], aData[3][2], aData[3][3]);
  }

  static Matrix FromColumnMajor(const float aData[16]) {
    return Matrix(
        aData[0], aData[1], aData[2], aData[3],
        aData[4], aData[5], aData[6], aData[7],
        aData[8], aData[9], aData[10], aData[11],
        aData[12], aData[13], aData[14], aData[15]);
  }

  static Matrix FromRowMajor(const float aData[4][4]) {
    return Matrix(
        aData[0][0], aData[1][0], aData[2][0], aData[3][0],
        aData[0][1], aData[1][1], aData[2][1], aData[3][1],
        aData[0][2], aData[1][2], aData[2][2], aData[3][2],
        aData[0][3], aData[1][3], aData[2][3], aData[3][3]);
  }

  static Matrix FromRowMajor(const float aData[16]) {
    return Matrix(
      aData[0], aData[4], aData[8], aData[12],
      aData[1], aData[5], aData[9], aData[13],
      aData[2], aData[6], aData[10], aData[14],
      aData[3], aData[7], aData[11], aData[15]);
  }

  static Matrix Position(const Vector& aPosition) {
    return Identity().TranslateInPlace(aPosition);
  }

  static Matrix Translation(const Vector& aTranslation) {
    return Identity().TranslateInPlace(aTranslation);
  }

  static Matrix Rotation(const Vector& aAxis, const float aRotation) {
    Matrix result;
    Vector normalized(aAxis.Normalize());
    const float x = normalized.x();
    const float y = normalized.y();
    const float z = normalized.z();
    const float angleSin = std::sin(aRotation);
    const float angleCos = std::cos(aRotation);
    const float oneMinusAngleCos(1.0f - angleCos);
    result.m.m[0][0] =      (angleCos) + (x * x * oneMinusAngleCos);
    result.m.m[1][0] = (-z * angleSin) + (x * y * oneMinusAngleCos);
    result.m.m[2][0] =  (y * angleSin) + (x * z * oneMinusAngleCos);
    result.m.m[0][1] =  (z * angleSin) + (y * x * oneMinusAngleCos);
    result.m.m[1][1] =      (angleCos) + (y * y * oneMinusAngleCos);
    result.m.m[2][1] = (-x * angleSin) + (y * z * oneMinusAngleCos);
    result.m.m[0][2] = (-y * angleSin) + (z * x * oneMinusAngleCos);
    result.m.m[1][2] =  (x * angleSin) + (z * y * oneMinusAngleCos);
    result.m.m[2][2] =      (angleCos) + (z * z * oneMinusAngleCos);
    result.m.m[3][3] = 1;

    return result;
  }

  static Matrix Rotation(const Vector& aDirection, const Vector& aUp = vrb::Vector(0.0f, 1.0f, 0.0f)) {
    Matrix result;
    const vrb::Vector dir = aDirection.Normalize();
    const vrb::Vector xaxis = aUp.Cross(dir).Normalize();
    const vrb::Vector yaxis = dir.Cross(xaxis).Normalize();

    result.m.m[0][0] = xaxis.x();
    result.m.m[1][0] = yaxis.x();
    result.m.m[2][0] = dir.x();
    result.m.m[0][1] = xaxis.y();
    result.m.m[1][1] = yaxis.y();
    result.m.m[2][1] = dir.y();
    result.m.m[0][2] = xaxis.z();
    result.m.m[1][2] = yaxis.z();
    result.m.m[2][2] = dir.z();
    result.m.m[3][3] = 1;

    return result;
  }

  static Matrix Rotation(const Quaternion & aQuat) {
    Matrix result;

    const float  qxx = aQuat.x() * aQuat.x();
    const float  qyy = aQuat.y() * aQuat.y();
    const float  qzz = aQuat.z() * aQuat.z();
    const float  qxz = aQuat.x() * aQuat.z();
    const float  qxy = aQuat.x() * aQuat.y();
    const float  qyz = aQuat.y() * aQuat.z();
    const float  qwx = aQuat.w() * aQuat.x();
    const float  qwy = aQuat.w() * aQuat.y();
    const float  qwz = aQuat.w() * aQuat.z();

    auto& m = result.m.m;
    m[0][0] = 1.0f - 2.0f * (qyy +  qzz);
    m[0][1] = 2.0f * (qxy + qwz);
    m[0][2] = 2.0f * (qxz - qwy);
    m[0][3] = 0.0f;
    m[1][0] = 2.0f * (qxy - qwz);
    m[1][1] = 1.0f - 2.0f * (qxx +  qzz);
    m[1][2] = 2.0f * (qyz + qwx);
    m[1][3] = 0.0f;
    m[2][0] = 2.0f * (qxz + qwy);
    m[2][1] = 2.0f * (qyz - qwx);
    m[2][2] = 1.0f - 2.0f * (qxx +  qyy);
    m[2][3] = 0.0f;
    m[3][0] = 0.0f;
    m[3][1] = 0.0f;
    m[3][2] = 0.0f;
    m[3][3] = 1.0f;

    return result;
  }


  static Matrix PerspectiveMatrix(
      const float aLeft, const float aRight, const float aTop, const float aBottom,
      const float aNear, const float aFar) {

    Matrix result;

    const float left = -std::tan(aLeft) * aNear;
    const float right = std::tan(aRight) * aNear;
    const float bottom = -std::tan(aBottom) * aNear;
    const float top = std::tan(aTop) * aNear;
    //VRB_LOG("left=%f right=%f bottom=%f top=%f",left,right,bottom,top);

    if ((left < right) && (bottom < top) &&
        (aNear < aFar) && (aNear > 0.0f) && (aFar > 0.0f)) {

      const float a00 = (2 * aNear) / (right - left);
      const float a11 = (2 * aNear) / (top - bottom);
      const float a20 = (right + left) / (right - left);
      const float a21 = (top + bottom) / (top - bottom);
      const float a22 = (aNear + aFar) / (aNear - aFar);
      const float a32 = (2 * aNear * aFar) / (aNear - aFar);

      result.m.m[0][0] = a00;
      result.m.m[2][0] = a20;
      result.m.m[1][1] = a11;
      result.m.m[2][1] = a21;
      result.m.m[2][2] = a22;
      result.m.m[3][2] = a32;
      result.m.m[2][3] = -1;
    }

    return result;
  }

  static Matrix PerspectiveMatrixFromDegrees(
      const float aLeft, const float aRight, const float aTop, const float aBottom,
      const float aNear, const float aFar) {
    return PerspectiveMatrix(
        aLeft * ToRadians, aRight * ToRadians,
        aTop * ToRadians, aBottom * ToRadians,
        aNear, aFar);
  }

  static Matrix PerspectiveMatrixWithResolution(
      const float aWidth, const float aHeight,
      const float aFovX, const float aFovY,
      const float aNear, const float aFar) {
    float fovX = aFovX, fovY = aFovY;
    if ((aFovX <= 0.0f) && (aFovY <= 0.0f)) {
      if (aWidth > aHeight) { fovX = 60.0f; }
      else { fovY = 60.0f; }
    }
    if (fovY <= 0.0f) {
      fovY = 2.0f * std::atan(std::tan(fovX * 0.5f) * (aHeight / aWidth));
    } else if (fovX <= 0.0f) {
      fovX = 2.0f * std::atan(std::tan(fovY * 0.5f) * (aWidth / aHeight));
    }
    fovX *= 0.5f;
    fovY *= 0.5f;
    return PerspectiveMatrix(fovX, fovX, fovY, fovY, aNear, aFar);
  }

  static Matrix PerspectiveMatrixWithResolutionDegrees(
      const float aWidth, const float aHeight,
      const float aFovX, const float aFovY,
      const float aNear, const float aFar) {
    return PerspectiveMatrixWithResolution(
        aWidth, aHeight,
        aFovX > 0.0f ? aFovX * ToRadians : aFovX,
        aFovY > 0.0f ? aFovY * ToRadians : aFovY,
        aNear, aFar);
  }

  Matrix() : m(
      0.0f, 0.0f, 0.0f, 0.0f,
      0.0f, 0.0f, 0.0f, 0.0f,
      0.0f, 0.0f, 0.0f, 0.0f,
      0.0f, 0.0f, 0.0f, 0.0f) {}

  Matrix(
      float a00, float a01, float a02, float a03,
      float a10, float a11, float a12, float a13,
      float a20, float a21, float a22, float a23,
      float a30, float a31, float a32, float a33) :
      m(
      a00, a01, a02, a03,
      a10, a11, a12, a13,
      a20, a21, a22, a23,
      a30, a31, a32, a33) {}

  float& At(const int32_t aColumn, const int32_t aRow) {
    if ((aColumn < 0) || (aColumn > 3) || (aRow < 0) || (aRow > 3)) {
      static float sDummy;
      return sDummy;
    }
    return m.m[aColumn][aRow];
  }

  float At(const int32_t aColumn, const int32_t aRow) const {
    if ((aColumn < 0) || (aColumn > 3) || (aRow < 0) || (aRow > 3)) {
      return 0.0f;
    }
    return m.m[aColumn][aRow];
  }

  Matrix(const Matrix& aValue) : m(aValue.m) {}

  Matrix& operator=(const Matrix& aMatrix) {
    m.Copy(aMatrix.m);
    return *this;
  }

  Matrix& SetIdentity() {
    m.m00 = 1.0f; m.m01 = 0.0f; m.m02 = 0.0f; m.m03 = 0.0f;
    m.m10 = 0.0f; m.m11 = 1.0f; m.m12 = 0.0f; m.m13 = 0.0f;
    m.m20 = 0.0f; m.m21 = 0.0f; m.m22 = 1.0f; m.m23 = 0.0f;
    m.m30 = 0.0f; m.m31 = 0.0f; m.m32 = 0.0f; m.m33 = 1.0f;
    return *this;
  }

  bool IsIdentity() const {
    return VRB_IS_ZERO(m.m00 - 1.0f) && VRB_IS_ZERO(m.m01) && VRB_IS_ZERO(m.m02) && VRB_IS_ZERO(m.m03)
        && VRB_IS_ZERO(m.m10) && VRB_IS_ZERO(m.m11 - 1.0f) && VRB_IS_ZERO(m.m12) && VRB_IS_ZERO(m.m13)
        && VRB_IS_ZERO(m.m20) && VRB_IS_ZERO(m.m21) && VRB_IS_ZERO(m.m22 - 1.0f) && VRB_IS_ZERO(m.m23)
        && VRB_IS_ZERO(m.m30) && VRB_IS_ZERO(m.m31) && VRB_IS_ZERO(m.m32) && VRB_IS_ZERO(m.m33 - 1.0f);
  }

  Vector MultiplyPosition(const Vector& aPosition) const {
    Vector result;
    result.x() = m.m00 * aPosition.x() + m.m10 * aPosition.y() + m.m20 * aPosition.z() + m.m30;
    result.y() = m.m01 * aPosition.x() + m.m11 * aPosition.y() + m.m21 * aPosition.z() + m.m31;
    result.z() = m.m02 * aPosition.x() + m.m12 * aPosition.y() + m.m22 * aPosition.z() + m.m32;
    const float w = m.m03 * aPosition.x() + m.m13 * aPosition.y() + m.m23 * aPosition.z() + m.m33;
    if ((w != 0) && (w != 1)) {
      result /= w;
    }

    return result;
  }

  Vector MultiplyDirection(const Vector& aDirection) const {
    Vector result;
    result.x() = m.m00 * aDirection.x() + m.m10 * aDirection.y() + m.m20 * aDirection.z();
    result.y() = m.m01 * aDirection.x() + m.m11 * aDirection.y() + m.m21 * aDirection.z();
    result.z() = m.m02 * aDirection.x() + m.m12 * aDirection.y() + m.m22 * aDirection.z();

    const float w = m.m03 * aDirection.x() + m.m13 * aDirection.y() + m.m23 * aDirection.z();
    if ((w != 0) && (w != 1)) {
      result /= w;
    }


    return result;
  }

  Matrix PreMultiply(const Matrix& aMatrix) const {
    Matrix result;
    for(int ix = 0; ix < 4; ix++) {
      for(int jy = 0; jy < 4; jy++) {
        float sum = 0;
        for(int kz = 0; kz < 4; kz++) {
           sum += aMatrix.m.m[kz][jy] * m.m[ix][kz];
        }
        result.m.m[ix][jy] = sum;
      }
    }

    return result;
  }

  Matrix PostMultiply(const Matrix& aMatrix) const {
    Matrix result;
    for(int ix = 0; ix < 4; ix++) {
      for(int jy = 0; jy < 4; jy++) {
        float sum = 0;
        for(int kz = 0; kz < 4; kz++) {
           sum += m.m[kz][jy] * aMatrix.m.m[ix][kz];
        }
        result.m.m[ix][jy] = sum;
      }
    }

    return result;
  }

  Matrix& PreMultiplyInPlace(const Matrix& aMatrix) {
    *this = PreMultiply(aMatrix);
    return *this;
  }

  Matrix& PostMultiplyInPlace(const Matrix& aMatrix) {
    *this = PostMultiply(aMatrix);
    return *this;
  }

  Matrix Transpose() const {
    return Matrix(
      m.m00, m.m10, m.m20, m.m30,
      m.m01, m.m11, m.m21, m.m31,
      m.m02, m.m12, m.m22, m.m32,
      m.m03, m.m13, m.m23, m.m33);
  }

  Vector GetTranslation() const {
    return Vector(m.m[3][0], m.m[3][1], m.m[3][2]);
  }

  Matrix& TranslateInPlace(const Vector& aOffset) {
    m.m[3][0] += aOffset.x();
    m.m[3][1] += aOffset.y();
    m.m[3][2] += aOffset.z();
    return *this;
  }

  Matrix Translate(const Vector& aOffset) const {
    Matrix result(*this);
    result.TranslateInPlace(aOffset);
    return result;
  }

  Vector GetScale() const {
    return Vector(m.m[0][0], m.m[1][1], m.m[2][2]);
  }

  Matrix& ScaleInPlace(const Vector& aScale) {
    m.m[0][0] *= aScale.x();
    m.m[1][1] *= aScale.y();
    m.m[2][2] *= aScale.z();
    return *this;
  }

  Matrix Scale(const Vector& aScale) const {
    Matrix result(*this);
    result.ScaleInPlace(aScale);
    return result;
  }

  Matrix AfineInverse() const {
    const float c00 =   m.m11 * m.m22 - m.m12 * m.m21;
    const float c10 = -(m.m01 * m.m22 - m.m02 * m.m21);
    const float c20 =   m.m01 * m.m12 - m.m02 * m.m11;
    const float c01 = -(m.m10 * m.m22 - m.m12 * m.m20);
    const float c11 =   m.m00 * m.m22 - m.m02 * m.m20;
    const float c21 = -(m.m00 * m.m12 - m.m02 * m.m10);
    const float c02 =   m.m10 * m.m21 - m.m11 * m.m20;
    const float c12 = -(m.m00 * m.m21 - m.m01 * m.m20);
    const float c22 =   m.m00 * m.m11 - m.m01 * m.m10;

    const float det = m.m00*c00 + m.m10*c10 + m.m20 * c20;
    if (VRB_IS_ZERO(det)) {
      return Matrix::Identity();
    }
    const float i00 = c00 / det,  i10 = c01 / det,  i20 = c02 / det;
    const float i01 = c10 / det,  i11 = c11 / det,  i21 = c12 / det;
    const float i02 = c20 / det,  i12 = c21 / det,  i22 = c22 / det;

    return Matrix(
        i00, i01, i02, 0.0f,
        i10, i11, i12, 0.0f,
        i20, i21, i22, 0.0f,
        -(i00 * m.m30 + i10 * m.m31 + i20 * m.m32),
        -(i01 * m.m30 + i11 * m.m31 + i21 * m.m32),
        -(i02 * m.m30 + i12 * m.m31 + i22 * m.m32),
        1);
  }

  Matrix Inverse() const {
    Matrix result;
    float *inv = result.Data();
    const float *data = Data();
    float det = 0.0f;

    inv[0] = data[5]  * data[10] * data[15] -
             data[5]  * data[11] * data[14] -
             data[9]  * data[6]  * data[15] +
             data[9]  * data[7]  * data[14] +
             data[13] * data[6]  * data[11] -
             data[13] * data[7]  * data[10];

    inv[4] = -data[4]  * data[10] * data[15] +
              data[4]  * data[11] * data[14] +
              data[8]  * data[6]  * data[15] -
              data[8]  * data[7]  * data[14] -
              data[12] * data[6]  * data[11] +
              data[12] * data[7]  * data[10];

    inv[8] = data[4]  * data[9] * data[15] -
             data[4]  * data[11] * data[13] -
             data[8]  * data[5] * data[15] +
             data[8]  * data[7] * data[13] +
             data[12] * data[5] * data[11] -
             data[12] * data[7] * data[9];

    inv[12] = -data[4]  * data[9] * data[14] +
               data[4]  * data[10] * data[13] +
               data[8]  * data[5] * data[14] -
               data[8]  * data[6] * data[13] -
               data[12] * data[5] * data[10] +
               data[12] * data[6] * data[9];

    inv[1] = -data[1]  * data[10] * data[15] +
              data[1]  * data[11] * data[14] +
              data[9]  * data[2] * data[15] -
              data[9]  * data[3] * data[14] -
              data[13] * data[2] * data[11] +
              data[13] * data[3] * data[10];

    inv[5] = data[0]  * data[10] * data[15] -
             data[0]  * data[11] * data[14] -
             data[8]  * data[2] * data[15] +
             data[8]  * data[3] * data[14] +
             data[12] * data[2] * data[11] -
             data[12] * data[3] * data[10];

    inv[9] = -data[0]  * data[9] * data[15] +
              data[0]  * data[11] * data[13] +
              data[8]  * data[1] * data[15] -
              data[8]  * data[3] * data[13] -
              data[12] * data[1] * data[11] +
              data[12] * data[3] * data[9];

    inv[13] = data[0]  * data[9] * data[14] -
              data[0]  * data[10] * data[13] -
              data[8]  * data[1] * data[14] +
              data[8]  * data[2] * data[13] +
              data[12] * data[1] * data[10] -
              data[12] * data[2] * data[9];

    inv[2] = data[1]  * data[6] * data[15] -
             data[1]  * data[7] * data[14] -
             data[5]  * data[2] * data[15] +
             data[5]  * data[3] * data[14] +
             data[13] * data[2] * data[7] -
             data[13] * data[3] * data[6];

    inv[6] = -data[0]  * data[6] * data[15] +
              data[0]  * data[7] * data[14] +
              data[4]  * data[2] * data[15] -
              data[4]  * data[3] * data[14] -
              data[12] * data[2] * data[7] +
              data[12] * data[3] * data[6];

    inv[10] = data[0]  * data[5] * data[15] -
              data[0]  * data[7] * data[13] -
              data[4]  * data[1] * data[15] +
              data[4]  * data[3] * data[13] +
              data[12] * data[1] * data[7] -
              data[12] * data[3] * data[5];

    inv[14] = -data[0]  * data[5] * data[14] +
               data[0]  * data[6] * data[13] +
               data[4]  * data[1] * data[14] -
               data[4]  * data[2] * data[13] -
               data[12] * data[1] * data[6] +
               data[12] * data[2] * data[5];

    inv[3] = -data[1] * data[6] * data[11] +
              data[1] * data[7] * data[10] +
              data[5] * data[2] * data[11] -
              data[5] * data[3] * data[10] -
              data[9] * data[2] * data[7] +
              data[9] * data[3] * data[6];

    inv[7] = data[0] * data[6] * data[11] -
             data[0] * data[7] * data[10] -
             data[4] * data[2] * data[11] +
             data[4] * data[3] * data[10] +
             data[8] * data[2] * data[7] -
             data[8] * data[3] * data[6];

    inv[11] = -data[0] * data[5] * data[11] +
               data[0] * data[7] * data[9] +
               data[4] * data[1] * data[11] -
               data[4] * data[3] * data[9] -
               data[8] * data[1] * data[7] +
               data[8] * data[3] * data[5];

    inv[15] = data[0] * data[5] * data[10] -
              data[0] * data[6] * data[9] -
              data[4] * data[1] * data[10] +
              data[4] * data[2] * data[9] +
              data[8] * data[1] * data[6] -
              data[8] * data[2] * data[5];

    det = data[0] * inv[0] + data[1] * inv[4] + data[2] * inv[8] + data[3] * inv[12];

    if (det == 0.0f) {
        return Identity();
    }

    det = 1.0f / det;

    for (int ix = 0; ix < 16; ix++) {
        inv[ix] = inv[ix] * det;
    }

    return result;
  }

  void DecomposePerspective(float& aLeft, float& aRight, float& aTop, float& aBottom, float& aNear, float& aFar) {
    aNear   = m.m[3][2] / (m.m[2][2] - 1.0f);
    aFar    = m.m[3][2] / (m.m[2][2] + 1.0f);
    aBottom = -atan2f((m.m[2][1] - 1.0f), m.m[1][1]);
    aTop    = atan2f((m.m[2][1] + 1.0f), m.m[1][1]);
    aLeft   = -atan2f((m.m[2][0] - 1.0f), m.m[0][0]);
    aRight  = atan2f((m.m[2][0] + 1.0f), m.m[0][0]);
  }

  void DecomposePerspectiveDegrees(float& aLeft, float& aRight, float& aTop, float& aBottom, float& aNear, float& aFar) {
    DecomposePerspective(aLeft, aRight, aTop, aBottom, aNear, aFar);
    aTop *= ToDegrees;
    aRight *= ToDegrees;
    aBottom *= ToDegrees;
    aLeft *= ToDegrees;
  }

  float* Data() { return reinterpret_cast<float*>(&(m.m)); }
  const float* Data () const { return reinterpret_cast<const float*>(&(m.m)); }

  std::string ToString() const {
    std::string result;
    for(int ix = 0; ix < 4; ix++) {
      result += "[";
      for(int jy = 0; jy < 4; jy++) {
        if (jy != 0) { result += ", "; }
        result += std::to_string(m.m[ix][jy]);

      }
      result += "]";
    }
    return result;
  }

protected:
  typedef union data {
    float m[4][4];
    struct {
      float m00, m01, m02, m03;
      float m10, m11, m12, m13;
      float m20, m21, m22, m23;
      float m30, m31, m32, m33;
    };

    void Copy(const data& aData) {
      for(int ix = 0; ix < 4; ix++) {
        for(int jy = 0; jy < 4; jy++) {
          m[ix][jy] = aData.m[ix][jy];
        }
      }
    }

    data(
        float a00, float a01, float a02, float a03,
        float a10, float a11, float a12, float a13,
        float a20, float a21, float a22, float a23,
        float a30, float a31, float a32, float a33) :
        m00(a00), m01(a01), m02(a02), m03(a03),
        m10(a10), m11(a11), m12(a12), m13(a13),
        m20(a20), m21(a21), m22(a22), m23(a23),
        m30(a30), m31(a31), m32(a32), m33(a33) {}

    data(const data& aData) {
      Copy(aData);
    }
  } data_t;

  data_t m;
};

}

#endif // VRB_MATRIX_DOT_H
