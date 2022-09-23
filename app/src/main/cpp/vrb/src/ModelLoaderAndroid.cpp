/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "vrb/ModelLoaderAndroid.h"
#include "vrb/ConcreteClass.h"

#include "vrb/Group.h"
#include "vrb/ClassLoaderAndroid.h"
#include "vrb/ConditionVariable.h"
#include "vrb/ContextSynchronizer.h"
#include "vrb/CreationContext.h"
#include "vrb/FileReaderAndroid.h"
#include "vrb/Logger.h"
#include "vrb/NodeFactoryObj.h"
#include "vrb/ParserObj.h"
#include "vrb/SharedEGLContext.h"
#include "vrb/RenderContext.h"
#include "vrb/ThreadUtils.h"

#include <pthread.h>
#include <vector>

namespace vrb {

#define LOCAL_CLOCK_TYPE CLOCK_THREAD_CPUTIME_ID

class LoadTimer {
public:
  LoadTimer() {}
  void Start() {
    clock_gettime(LOCAL_CLOCK_TYPE, &start);
  }
  float Sample() {
    timespec end;
    clock_gettime(LOCAL_CLOCK_TYPE, &end);
    return (float) end.tv_sec + (((float) end.tv_nsec) / 1.0e9f) - ((float) start.tv_sec +
           (((float) start.tv_nsec) / 1.0e9f));
  }
protected:
  timespec start;
};

#undef LOCAL_CLOCK_TYPE

static LoadFinishedCallback sNoop = [](GroupPtr&){};

struct LoadInfo {
  GroupPtr target;
  LoadTask task;
  LoadFinishedCallback callback;
  LoadInfo(GroupPtr& aGroup, LoadTask& aTask, LoadFinishedCallback& aCallback)
      : target(aGroup)
      , task(aTask)
      , callback(aCallback)
  {}
  LoadInfo(const LoadInfo& aInfo)
      : target(aInfo.target)
      , task(aInfo.task)
      , callback(aInfo.callback)
  {}
  LoadInfo& operator=(const LoadInfo& aInfo) {
    target = aInfo.target;
    task = aInfo.task;
    callback = aInfo.callback;
    return *this;
  }
private:
  LoadInfo() = delete;
};

class ModelLoaderAndroidSynchronizerObserver;
typedef std::shared_ptr<ModelLoaderAndroidSynchronizerObserver> ModelLoaderAndroidSynchronizerObserverPtr;

class ModelLoaderAndroidSynchronizerObserver : public ContextSynchronizerObserver {
public:
  static ModelLoaderAndroidSynchronizerObserverPtr Create() {
    return std::make_shared<ModelLoaderAndroidSynchronizerObserver>();
  }
  void Set(GroupPtr& aSource, GroupPtr& aTarget, LoadFinishedCallback& aCallback) {
    mSource = aSource;
    mTarget = aTarget;
    mCallback = aCallback;
  }

  void AddFinishedCallbacks(std::vector<LoadFinishedCallback>& aCallbacks) {
    finishCallbacks.swap(aCallbacks);
  }

  void ContextsSynchronized(RenderContextPtr& aRenderContext) override {
    if (mTarget && mSource) {
      mTarget->TakeChildren(mSource);
    }
    mCallback(mTarget);
    mTarget = mSource = nullptr;
    mCallback = sNoop;
    GroupPtr nullGroup;
    for (LoadFinishedCallback& cb : finishCallbacks) {
      if (cb) {
        cb(nullGroup);
      }
    }
    finishCallbacks.clear();
  }
  ModelLoaderAndroidSynchronizerObserver() {}
protected:
  GroupPtr mSource;
  GroupPtr mTarget;
  LoadFinishedCallback mCallback;
  std::vector<LoadFinishedCallback> finishCallbacks;
private:
  VRB_NO_DEFAULTS(ModelLoaderAndroidSynchronizerObserver)
};

struct ModelLoaderAndroid::State {
  bool running;
  JavaVM* jvm;
  JNIEnv* renderThreadEnv;
  JNIEnv* env;
  jobject activity;
  jobject assets;
  RenderContextWeak render;
  CreationContextPtr context;
  SharedEGLContextPtr eglContext;
  pthread_t child;
  ConditionVariable loadLock;
  bool done;
  bool quitting;
  std::vector<LoadInfo> loadList;
  std::vector<LoadFinishedCallback> finishCallbacks;
  State()
      : running(false)
      , jvm(nullptr)
      , renderThreadEnv(nullptr)
      , env(nullptr)
      , activity(nullptr)
      , assets(nullptr)
      , done(false)
      , quitting(false)
  {}
  void StartThread() {
    if (running) {
      return;
    }
    if (!renderThreadEnv || !eglContext) {
      return;
    }
    done = false;
    pthread_create(&child, nullptr, &ModelLoaderAndroid::Run, this);
    running = true;
  }

  void StopThread() {
    if (!running) {
      return;
    }
    RenderContextPtr context = render.lock();
    if (context) {
      context->Update();
    }
    VRB_LOG("Waiting for ModelLoaderAndroid load thread to stop.");
    {
      MutexAutoLock lock(loadLock);
      done = true;
      loadLock.Signal();
    }
    bool gotQuit = false;
    while (!gotQuit) {
      if (context) {
        context->Update();
      }
      MutexAutoLock lock(loadLock);
      gotQuit = quitting;
    }
    if (pthread_join(child, nullptr) == 0) {
      VRB_LOG("ModelLoaderAndroid load thread stopped");
    } else {
      VRB_ERROR("ModelLoaderAndroid load thread failed to stop");
    }
    running = false;
  }

  bool
  IsOnLoaderThread() const {
    return running && (pthread_equal(child, pthread_self()) > 0);
  }
};

ModelLoaderAndroidPtr
ModelLoaderAndroid::Create(RenderContextPtr& aContext) {

  return std::make_shared<ConcreteClass<ModelLoaderAndroid, ModelLoaderAndroid::State> >(aContext);
}

void
ModelLoaderAndroid::InitializeJava(JNIEnv* aEnv, jobject aActivity, jobject aAssets) {
  if (m.running) {
    ShutdownJava();
  }
  if (aEnv->GetJavaVM(&(m.jvm)) != 0) {
    return;
  }
  m.renderThreadEnv = aEnv;
  m.activity = aEnv->NewGlobalRef(aActivity);
  m.assets = aEnv->NewGlobalRef(aAssets);
  m.StartThread();
}

void
ModelLoaderAndroid::ShutdownJava() {
  m.StopThread();
  if (!m.renderThreadEnv) {
    return;
  }
  if (m.activity) {
    m.renderThreadEnv->DeleteGlobalRef(m.activity);
    m.activity = nullptr;
  }
  if (m.assets) {
    m.renderThreadEnv->DeleteGlobalRef(m.assets);
    m.assets = nullptr;
  }
  m.renderThreadEnv = nullptr;
}

void
ModelLoaderAndroid::InitializeGL() {
  m.eglContext = SharedEGLContext::Create();
  m.eglContext->Initialize();
  m.StartThread();
}

void
ModelLoaderAndroid::ShutdownGL() {
  m.StopThread();
  m.eglContext = nullptr;
}

void
ModelLoaderAndroid::LoadModel(const std::string& aModelName, GroupPtr aTargetNode) {
  LoadModel(aModelName, std::move(aTargetNode), sNoop);
}

void
ModelLoaderAndroid::LoadModel(vrb::LoadTask aLoadTask, GroupPtr aTargetNode) {
  RunLoadTask(std::move(aTargetNode), aLoadTask, sNoop);
}

void
ModelLoaderAndroid::LoadModel(const std::string& aModelName, GroupPtr aTargetNode, LoadFinishedCallback& aCallback) {
  LoadTask task = [aModelName](CreationContextPtr& aContext) -> GroupPtr {
    LoadTimer timer;
    timer.Start();
    NodeFactoryObjPtr factory = NodeFactoryObj::Create(aContext);
    ParserObjPtr parser = ParserObj::Create(aContext);
    parser->SetFileReader(aContext->GetFileReader());
    parser->SetObserver(factory);
    GroupPtr group = Group::Create(aContext);
    factory->SetModelRoot(group);
    parser->LoadModel(aModelName);
    VRB_LOG("TIMER Load time for %s: %f sec", aModelName.c_str(), timer.Sample());
    return group;
  };
  RunLoadTask(std::move(aTargetNode), task, aCallback);
}

void
ModelLoaderAndroid::RunLoadTask(GroupPtr aTargetNode, LoadTask& aTask) {
  RunLoadTask(std::move(aTargetNode), aTask, sNoop);
}

void
ModelLoaderAndroid::RunLoadTask(GroupPtr aTargetNode, LoadTask& aTask, LoadFinishedCallback& aCallback) {
  MutexAutoLock lock(m.loadLock);
  m.loadList.emplace_back(LoadInfo(aTargetNode, aTask,  aCallback));
  m.loadLock.Signal();
}

/* static */ void*
ModelLoaderAndroid::Run(void* data) {
  ModelLoaderAndroid::State& m = *(ModelLoaderAndroid::State*)data;
  m.context->BindToThread();
  bool attached = false;
  if (m.jvm->AttachCurrentThread(&(m.env), nullptr) == 0) {
    SetThreadName("VRB Loader");
    attached = true;
    const bool offRenderThreadContextCurrent = m.eglContext->MakeCurrent();
    if (!offRenderThreadContextCurrent) {
      VRB_ERROR("Failed to make shared context current. VRB Nodes will be initialized on render thread");
    }
    ClassLoaderAndroidPtr classLoader = ClassLoaderAndroid::Create();
    classLoader->Init(m.env, m.activity);
    FileReaderAndroidPtr reader = FileReaderAndroid::Create();
    reader->Init(m.env, m.assets, classLoader);
    m.context->SetFileReader(reader);
    ModelLoaderAndroidSynchronizerObserverPtr finalizer = ModelLoaderAndroidSynchronizerObserver::Create();
    ContextSynchronizerObserverPtr obs = finalizer;
    m.context->RegisterContextSynchronizerObserver(obs);

    LoadTimer timer;
    LoadTimer total;
    float accumulativeTime = 0.0f;

    bool done = false;
    while (!done) {
      std::vector<LoadInfo> list;
      {
        MutexAutoLock lock(m.loadLock);
        list.swap(m.loadList);
        done = m.done;
        while (list.empty() && !done) {
          m.loadLock.Wait();
          list.swap(m.loadList);
          done = m.done;
        }
      }

      if (!done) {
        for (LoadInfo& info: list) {
          total.Start();
          timer.Start();
          GroupPtr group = info.task(m.context);
          VRB_DEBUG("TIMER Off-render-thread asset task: %f sec", timer.Sample());
          finalizer->Set(group, info.target, info.callback);
          if (offRenderThreadContextCurrent) {
            timer.Start();
            m.context->UpdateResourceGL();
            VRB_DEBUG("TIMER Update GL resources: %f sec", timer.Sample());
          }
          finalizer->AddFinishedCallbacks(m.finishCallbacks);
          timer.Start();
          m.context->Synchronize();
          const float thisLoad = total.Sample();
          accumulativeTime += thisLoad;
          VRB_DEBUG("TIMER Total asset processing time: %f sec", thisLoad);
          VRB_LOG("TIMER Accumulative time loading assets: %f sec", accumulativeTime);
        }
      }
    }

    m.env = nullptr;

    m.context->ReleaseContextSynchronizerObserver(obs);
  }
  if (attached) {
    m.jvm->DetachCurrentThread();
  }
  {
    MutexAutoLock lock(m.loadLock);
    m.quitting = true;
  }
  VRB_LOG("ModelLoaderAndroid load thread stopping");
  return nullptr;
}

void
ModelLoaderAndroid::AddFinishedCallback(LoadFinishedCallback& aCallback) {
  if (!m.IsOnLoaderThread()) {
    VRB_ERROR("ModelLoaderAndroid::AddFinisedhCallback must be called on the loading thread");
  }
  m.finishCallbacks.emplace_back(aCallback);
}

bool
ModelLoaderAndroid::IsOnLoaderThread() const {
  return m.IsOnLoaderThread();
}


ModelLoaderAndroid::ModelLoaderAndroid(State& aState, RenderContextPtr& aContext)
    : m(aState) {
  m.context = CreationContext::Create(aContext);
  m.render = aContext;
}

ModelLoaderAndroid::~ModelLoaderAndroid() {
  m.StopThread();
}


} // namespace vrb
