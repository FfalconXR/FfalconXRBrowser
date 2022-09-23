/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "vrb/FileReaderAndroid.h"
#include "vrb/ConcreteClass.h"

#include "vrb/ClassLoaderAndroid.h"
#include "vrb/JNIException.h"
#include "vrb/Logger.h"


#include <jni.h>
#include <fstream>
#include <vector>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
    Java_org_mozilla_vrb_ImageLoader_##method_name


namespace {

inline jlong jptr(vrb::FileReaderAndroid* ptr) { return reinterpret_cast<intptr_t>(ptr); }
inline vrb::FileReaderAndroid* ptr(jlong jptr) { return reinterpret_cast<vrb::FileReaderAndroid*>(jptr); }

}

namespace vrb {

struct FileReaderAndroid::State {
  int trackingHandleCount;
  JNIEnv* env;
  jobject jassetManager;
  AAssetManager* am;
  jclass imageLoaderClass;
  jmethodID loadFromAssets;
  jmethodID loadFromRawFile;
  int imageTargetHandle;
  FileHandlerPtr imageTarget;
  State()
      : trackingHandleCount(0)
      , env(nullptr)
      , jassetManager(nullptr)
      , am(nullptr)
      , imageLoaderClass(nullptr)
      , loadFromAssets(nullptr)
      , loadFromRawFile(nullptr)
      , imageTargetHandle(0)
  {}

  int nextHandle() {
    trackingHandleCount++;
    return trackingHandleCount;
  }

  void readRawAssetsFile(const std::string& aFileName, FileHandlerPtr aHandler) {
    const int handle = nextHandle();
    aHandler->BindFileHandle(aFileName, handle);
    if (!am) {
      aHandler->LoadFailed(handle, "Unable to load file: No Android AssetManager.");
      return;
    }

    AAsset* asset = AAssetManager_open(am, aFileName.c_str(), AASSET_MODE_STREAMING);
    if (!asset) {
      aHandler->LoadFailed(handle, "Unable to find file");
      return;
    }

    const int bufferSize = 1024;
    char buffer[bufferSize];
    int read = 0;
    while ((read = AAsset_read(asset, buffer, bufferSize)) > 0) {
      aHandler->ProcessRawFileChunk(handle, buffer, read);
    }
    if (read == 0) {
      aHandler->FinishRawFile(handle);
    } else {
      aHandler->LoadFailed(handle, "Error while reading file");
    }

    AAsset_close(asset);
  }

  void readRawFile(const std::string& aFileName, FileHandlerPtr aHandler) {
    const int handle = nextHandle();
    aHandler->BindFileHandle(aFileName, handle);
    std::ifstream input(aFileName, std::ios::binary);
    if (!input) {
      aHandler->LoadFailed(handle, "Unable to load file: No Android AssetManager.");
      return;
    }

    std::vector<char> buffer((std::istreambuf_iterator<char>(input)), std::istreambuf_iterator<char>());
    if (buffer.size() > 0) {
      aHandler->ProcessRawFileChunk(handle, buffer.data(), buffer.size());
      aHandler->FinishRawFile(handle);
    } else {
      aHandler->LoadFailed(handle, "Error while reading file");
    }
  }

private:
  State(const State&) = delete;
  State& operator=(const State&) = delete;
};

FileReaderAndroidPtr
FileReaderAndroid::Create() {
  return std::make_shared<ConcreteClass<FileReaderAndroid, FileReaderAndroid::State> >();
}

void
FileReaderAndroid::ReadRawFile(const std::string& aFileName, FileHandlerPtr aHandler) {
  if (aFileName.size() && aFileName[0] == '/') {
    m.readRawFile(aFileName, aHandler);
  } else {
    m.readRawAssetsFile(aFileName, aHandler);
  }
}

void
FileReaderAndroid::ReadImageFile(const std::string& aFileName, FileHandlerPtr aHandler) {
  if (!aHandler) {
    return;
  }
  m.imageTarget = aHandler;
  m.imageTargetHandle = m.nextHandle();
  m.imageTarget->BindFileHandle(aFileName, m.imageTargetHandle);
  if (!m.loadFromAssets || !m.am) {
    m.imageTarget->LoadFailed(m.imageTargetHandle, "FileReaderAndroid is not initialized.");
    return;
  }

  jstring jFileName = m.env->NewStringUTF(aFileName.c_str());
  if (aFileName.size() && aFileName[0] == '/') {
    m.env->CallStaticVoidMethod(m.imageLoaderClass, m.loadFromRawFile, jFileName, jptr(this), m.imageTargetHandle);
    VRB_CHECK_JNI_EXCEPTION(m.env);
  } else {
    m.env->CallStaticVoidMethod(m.imageLoaderClass, m.loadFromAssets, m.jassetManager, jFileName, jptr(this), m.imageTargetHandle);
    VRB_CHECK_JNI_EXCEPTION(m.env);
  }
  m.env->DeleteLocalRef(jFileName);
}

void
FileReaderAndroid::Init(JNIEnv* aEnv, jobject &aAssetManager, const ClassLoaderAndroidPtr& classLoader) {
  m.env = aEnv;
  if (!m.env) {
    return;
  }
  m.jassetManager = m.env->NewGlobalRef(aAssetManager);
  m.am = AAssetManager_fromJava(m.env, m.jassetManager);
  jclass localImageLoaderClass = classLoader->FindClass("org/mozilla/vrb/ImageLoader");
  m.imageLoaderClass = (jclass)m.env->NewGlobalRef(localImageLoaderClass);
  m.loadFromAssets = m.env->GetStaticMethodID(m.imageLoaderClass, "loadFromAssets", "(Landroid/content/res/AssetManager;Ljava/lang/String;JI)V");
  if (!m.loadFromAssets) {
    VRB_ERROR("Failed to find Java function ImageLoader::loadFromAssets");
  }
  m.loadFromRawFile = m.env->GetStaticMethodID(m.imageLoaderClass, "loadFromRawFile", "(Ljava/lang/String;JI)V");
  if (!m.loadFromRawFile) {
    VRB_ERROR("Failed to find Java function ImageLoader::loadFromRawfile");
  }
}

void
FileReaderAndroid::Shutdown() {
  if (m.env) {
    m.env->DeleteGlobalRef(m.jassetManager);
    m.env->DeleteGlobalRef(m.imageLoaderClass);
    m.am = nullptr;
    m.env = nullptr;
    m.loadFromAssets = 0;
  }
}

void
FileReaderAndroid::ProcessImageFile(const int aFileHandle, std::unique_ptr<uint8_t[]> &aImage, const uint64_t aImageLength, const int aWidth, const int aHeight, const GLenum aFormat) {
  if (m.imageTargetHandle != aFileHandle) {
    return;
  }

  m.imageTargetHandle = 0;
  if (!m.imageTarget) {
    return;
  }

  m.imageTarget->ProcessImageFile(aFileHandle, aImage, aImageLength, aWidth, aHeight, aFormat);
  m.imageTarget = nullptr;
}


void
FileReaderAndroid::ImageFileLoadFailed(const int aFileHandle, const std::string& aReason) {
  if (!m.imageTarget || (m.imageTargetHandle != aFileHandle)) {
    return;
  }

  m.imageTarget->LoadFailed(aFileHandle, aReason);
  m.imageTargetHandle = 0;
  m.imageTarget = nullptr;
}

FileReaderAndroid::FileReaderAndroid(State& aState) : m(aState) {}
FileReaderAndroid::~FileReaderAndroid() {}

} // namespace vrb

extern "C" {

JNI_METHOD(void, ProcessImage)
(JNIEnv* env, jclass, jlong aFileReaderAndroid, jint aFileTrackingHandle, jobject aBuffer, jint width, jint height, jint format) {
  vrb::FileReaderAndroid* reader = ptr(aFileReaderAndroid);

  if (!reader) {
    VRB_ERROR("FileReaderAndroid is nullptr in ProcessTexture");
    return;
  }

  uint8_t * buffer = (uint8_t *) env->GetDirectBufferAddress(aBuffer);
  jlong length = env->GetDirectBufferCapacity(aBuffer);
  if (length <= 0) {
    VRB_ERROR("FileReaderAndroid got invalid ByteBuffer length");
    return;
  }


  std::unique_ptr<uint8_t[]> image = std::make_unique<uint8_t[]>((size_t)length);
  memcpy(image.get(), buffer, (size_t)length);
  reader->ProcessImageFile(aFileTrackingHandle, image, (uint64_t) length, width, height, (GLenum) format);
}

JNI_METHOD(void, ImageLoadFailed)
(JNIEnv* env, jclass, jlong aFileReaderAndroid, jint aFileTrackingHandle, jstring aReason) {
  vrb::FileReaderAndroid* reader = ptr(aFileReaderAndroid);

  if (!reader) {
    VRB_ERROR("FileReaderAndroid is a nullptr in ImageLoadFailed");
    return;
  }

  const char *nativeString = env->GetStringUTFChars(aReason, 0);
  std::string reason = nativeString;
  env->ReleaseStringUTFChars(aReason, nativeString);

  reader->ImageFileLoadFailed(aFileTrackingHandle, reason);
}

} // extern "C"
