# Frames and Shadow DOM

The runtime assigns collision-free frame IDs and a separate derived document identity to every frame. Same-origin nested frames are observed and actionable, with coordinates transformed into the main viewport. Sandboxed and cross-origin frames are represented with explicit capability states instead of disappearing silently.

Open Shadow DOM is traversed for semantic content, actions, focused elements, and embedded frames. Closed roots that existed before runtime installation cannot be inspected. The optional experimental setting can force future roots open, but it changes page behavior and is disabled by default.

Frame and Shadow traversal have independent depth limits. Capability warnings and depth-limit warnings tell an agent when observation is incomplete.
