/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef VRB_NODE_DOT_H
#define VRB_NODE_DOT_H

#include "vrb/Forward.h"
#include "vrb/MacroUtils.h"

#include <functional>
#include <string>
#include <vector>

namespace vrb {

class Node {
public:
  const std::string& GetName() const;
  void SetName(const std::string& aName);
  void GetParents(std::vector<GroupPtr>& aParents) const;
  void RemoveFromParents();
  virtual void Cull(CullVisitor& aVisitor, DrawableList& aDrawables) = 0;
  using TraverseFunction = std::function<bool(const NodePtr& aNode, const GroupPtr& aTraversingFrom)>;
  static bool Traverse(const NodePtr& aRootNode, const TraverseFunction& aTraverseFunction);
protected:
  struct State;
  Node(State& aState, CreationContextPtr& aContext);
  virtual ~Node();
  static void AddToParents(GroupWeak& aParent, Node& aChild);
  static void RemoveFromParents(Group& aParent, Node& aChild);
  virtual bool Traverse(const GroupPtr& aParent, const TraverseFunction& aTraverseFunction);
private:
  State& m;
  Node() = delete;
  VRB_NO_DEFAULTS(Node)
};

} // namespace vrb

#endif // VRB_NODE_DOT_H
