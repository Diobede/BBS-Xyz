# Structure Form - Implementation TODO

## Overview
Structure form for whole-structure animation (trees, houses, terrain, large areas). Optimized for weak PCs with LOD system and chunk meshing.

---

## Phase 1: Core Implementation **(done)**

### Structure Loading & Storage **(done)**
- [x] Create `StructureForm.java` extending `Form` **(done)**
  - [x] Add `ValueString structurePath` - path to `.nbt` file **(done)**
  - [x] Add `ValueBoolean useCache` - cache rendering mesh **(done)**
  - [x] Add `ValueInt maxBlocks` - performance limit warning **(done)**
  - [x] Add `ValueFloat lodDistance1` - LOD level 1 distance (default: 64) **(done)**
  - [x] Add `ValueFloat lodDistance2` - LOD level 2 distance (default: 256) **(done)**
- [x] Support vanilla structure block `.nbt` format import **(done)**
- [x] Store structures in `config/bbs/assets/structures/` **(done)**
- [x] Implement NBT parser for structure block format **(done)**
- [x] Extract block palette and positions from NBT **(done)**

### Rendering System **(done)**
- [x] Create `StructureFormRenderer.java` implementing `IFormRenderer<StructureForm>` **(done)**
- [x] Implement per-block rendering using `renderBlock()` **(done)**
- [x] Implement per-block world lighting (sky light + block light) **(done)**
- [x] Structure caching system (global + instance cache) **(done)**
- [x] Block count limit enforcement **(done)**
- [x] Implement **occlusion culling** (skip blocks hidden inside structure) **(done - Phase 2)**
  - [x] Mark interior blocks as hidden **(done)**
  - [x] Only render exterior faces **(done)**
- [ ] Implement **chunk meshing** (combine blocks into single mesh like Sodium) **(Future - complex)**
  - [ ] Group blocks by material/texture
  - [ ] Merge adjacent faces of same block type
  - [ ] Build vertex buffer for entire structure
- [ ] Implement **GPU instancing** for repeated blocks **(Future - complex)**
  - [ ] Detect repeated block patterns
  - [ ] Use instanced rendering for stone, wood, etc.

---

## Phase 2: LOD System & Rendering Improvements

### Biome Color Support **(done)**
- [x] Implement biome-aware rendering for colored blocks **(done)**
  - [x] Create custom `BlockRenderView` wrapper for structure blocks **(done - StructureBlockView.java)**
  - [x] Store biome data in extended structure format (or detect from world) **(done - samples from world)**
  - [x] Apply biome tinting to leaves, grass, water blocks **(done - uses renderBlock())**
  - [x] Cache biome color calculations for performance **(done - Minecraft handles this)**
- [x] Fallback to neutral colors when biome data unavailable **(done - renderBlockAsEntity fallback)**

### LOD Level 0 (Close Range, < 64 blocks)
- [x] Render full detail with all blocks **(done)**
- [ ] Use chunk meshing for performance
- [x] Apply transforms (translate, rotate, scale) **(done)**
- [x] Fixed lighting to use proper world lighting instead of overly bright context.light **(done)**

### LOD Level 1 (Medium Range, 64-256 blocks)
- [x] Generate simplified mesh **(Partial - skips non-full blocks)**
  - [ ] Merge adjacent blocks of same type into larger cubes
  - [x] Reduce polygon count by skipping non-opaque blocks **(done)**
- [ ] Cache simplified mesh for reuse
- [x] Falls back to optimized rendering **(done)**

### LOD Level 2 (Far Range, > 256 blocks)
- [x] Render as single billboard/box **(done - Bounding Box)**
  - [ ] Pre-render structure from multiple angles (front, back, left, right, top, bottom)
  - [ ] Cache billboard textures
  - [ ] Face billboard toward camera
- [x] Or fallback: render colored bounding box **(done - Implemented)**
- [ ] Culling beyond far thresholds (optional)

### LOD Distance Management
- [x] Calculate camera distance to structure center **(done)**
- [x] Switch LOD levels based on distance thresholds **(done)**
- [x] User-configurable LOD distances in form properties **(done)**
- [ ] Smooth transitions between LOD levels (fade in/out)

---

## Phase 3: Optimization

### Memory Management
- [ ] **Lazy loading**: Load structure mesh only when visible/needed
- [ ] **Unload distant structures**: Free memory when structure far from camera
- [ ] **Compression**: Compress identical block patterns (run-length encoding)
- [ ] Implement structure mesh cache system
  - [ ] Cache by structure path + LOD level
  - [ ] LRU eviction when cache full

### Performance Limits
- [x] Auto-detect PC performance tier (partially handled by max blocks)
  - [ ] High performance: 100k blocks max
  - [ ] Medium performance: 10k blocks max
  - [ ] Weak PC: 1k blocks max, aggressive LOD
- [x] Display warning in UI if structure exceeds block limit **(done - implemented limit)**
- [ ] Suggest optimization options (increase LOD distances, reduce size)

---

## Phase 4: Large Structure Support
