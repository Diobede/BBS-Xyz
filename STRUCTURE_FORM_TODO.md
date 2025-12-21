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
- [x] Use chunk meshing for performance **(done - Phase 1)**
- [x] Apply transforms (translate, rotate, scale) **(done)**
- [x] Fixed lighting to use proper world lighting instead of overly bright context.light **(done)**

### LOD Level 1 (Medium Range, 64-256 blocks)
- [ ] Generate simplified mesh
  - [ ] Merge adjacent blocks of same type into larger cubes
  - [ ] Reduce polygon count by 50-70%
- [ ] Cache simplified mesh for reuse
- **NOTE**: Currently falls back to LOD 0 rendering (full detail)

### LOD Level 2 (Far Range, > 256 blocks)
- [ ] Render as single billboard texture
  - [ ] Pre-render structure from multiple angles (front, back, left, right, top, bottom)
  - [ ] Cache billboard textures
  - [ ] Face billboard toward camera
- [ ] Or fallback: render colored bounding box
- **NOTE**: Currently doesn't render at extreme distances (performance optimization)

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
- [ ] Auto-detect PC performance tier:
  - [ ] High performance: 100k blocks max
  - [ ] Medium performance: 10k blocks max
  - [ ] Weak PC: 1k blocks max, aggressive LOD
- [ ] Display warning in UI if structure exceeds block limit
- [ ] Suggest optimization options (increase LOD distances, reduce size)

---

## Phase 4: Large Structure Support

### Chunk-Scale Structures
- [ ] Support structures up to **32x32 chunks** (512x512 blocks)
- [ ] **Streaming system**: Load structure in 16x16 chunk sections
  - [ ] Only load chunks visible to camera
  - [ ] Unload chunks outside render distance
- [ ] **Region files**: Split huge structures into multiple `.nbt` files
  - [ ] `my_structure_0_0.nbt`, `my_structure_0_1.nbt`, etc.
  - [ ] Load/unload regions on demand

### Performance Warnings
- [ ] Show warning if structure > 50k blocks
- [ ] Recommend LOD settings for large structures
- [ ] Display estimated memory usage in UI

---

## Phase 5: Animation & Transform

- [ ] **Pivot point customization**:
  - [ ] Rotate around base (bottom center)
  - [ ] Rotate around center (middle)
  - [ ] Rotate around custom point (user-defined XYZ)

### Keyframeable Properties
- [ ] Add keyframe tracks to `StructureForm`:
  - [ ] `assemblyProgress` (Float) - 0.0 = nothing, 1.0 = fully built


### Assembly/Disassembly Animation
- [ ] Implement assembly progress animation:
  - [ ] **Bottom-to-top**: Blocks appear from bottom layer upward
  - [ ] **Top-to-bottom**: Blocks appear from top layer downward
  - [ ] **Center-outward**: Blocks appear from center radiating out
  - [ ] **Edge-inward**: Blocks appear from edges toward center
  - [ ] **Random order**: Blocks appear in random sequence
- [ ] Add `ValueList assemblyMode` to choose build order
- [ ] Add `ValueInt assemblyDuration` (ticks to fully build)
- [ ] Cache assembly order for performance

---

## Phase 6: UI & Editor

### Structure Picker
- [ ] Create `UIStructureFormPanel.java` extending `UIFormPanel<StructureForm>`
- [ ] Add **Structure path** textbox with file picker button
- [ ] Add **Browse structures** button to open structure browser
- [ ] Structure browser UI (like texture picker):
  - [ ] Grid view with structure previews
  - [ ] Search/filter by name
  - [ ] Display block count and estimated memory
- [ ] Add **Import from structure block** button
  - [ ] Detect nearby structure blocks
  - [ ] Load `.nbt` from structure block save

### Form Editor
- [ ] LOD settings panel:
  - [ ] LOD Distance 1 trackpad (default: 64)
  - [ ] LOD Distance 2 trackpad (default: 256)
  - [ ] Toggle LOD preview (show current LOD level)
- [ ] Performance settings:
  - [ ] Max blocks limit trackpad
  - [ ] Use cache toggle
  - [ ] Display current block count
  - [ ] Display estimated memory usage
- [ ] Transform settings:
  - [ ] Pivot point picker (base/center/custom)
  - [ ] Custom pivot XYZ trackpads
- [ ] Assembly settings:
  - [ ] Assembly mode dropdown
  - [ ] Assembly duration trackpad
  - [ ] Preview assembly animation button

### Visual Feedback
- [ ] Render bounding box in editor (like model block axes)
- [ ] Display current LOD level indicator
- [ ] Show pivot point marker in preview
- [ ] Performance warning icon if block count too high

---

## Phase 7: Special Effects

### Rendering Effects
- [ ] **Shadow casting**: Use vanilla shadow system
- [ ] **Lighting**: Blocks emit light (glowstone, torches, etc.)
  - [ ] Read light level from block properties
  - [ ] Apply dynamic lighting if mod present
- [ ] **Transparency**: Support glass, water, ice blocks
  - [ ] Render transparent blocks in separate pass
  - [ ] Sort transparent blocks back-to-front

### Fade Effects
- [ ] Implement visibility fade (0.0 to 1.0)
  - [ ] Alpha blending for entire structure
  - [ ] Smooth transitions in keyframe editor
- [ ] Implement color tinting
  - [ ] Apply color multiplier to all blocks
  - [ ] Preserve block texture colors

---

## Phase 8: Advanced Features (Optional)

### Additional Format Support
- [ ] Support **Litematica** schematics (`.litematic`)
- [ ] Support **WorldEdit** schematics (`.schem`, `.schematic`)
- [ ] Converter utility to convert formats to BBS `.nbt`

### Custom Structure Creator UI
- [ ] In-world area selection tool:
  - [ ] Cutom UI that could place pos 1 and pos 2 inside UI
- [ ] Visual structure editor:
  - [ ] Modify blocks before saving
  - [ ] Preview structure in editor
  - [ ] Copy/paste structure sections

### Procedural Generation
- [ ] Generate structures from code (trees, buildings)
- [ ] Randomization options (vary blocks, shapes)
- [ ] Integration with world generation

---

## Testing & Validation

### Performance Testing
- [ ] Test with 1k, 10k, 50k, 100k block structures
- [ ] Measure FPS impact on weak/medium/high-end PCs
- [ ] Validate LOD switching distances
- [ ] Test chunk streaming for huge structures

### Compatibility Testing
- [ ] Test with vanilla structure blocks
- [ ] Test with shaders (Iris/Optifine)
- [ ] Test with Sodium/Lithium
- [ ] Test with Distant Horizons

### Edge Cases
- [ ] Empty structure (0 blocks)
- [ ] Single block structure
- [ ] Structure with air-only blocks
- [ ] Structure exceeding block limit
- [ ] Invalid `.nbt` format

---

## Documentation

- [ ] Add Structure form to BBS wiki
- [ ] Document `.nbt` format compatibility
- [ ] Add tutorial: "Creating animated structures"
- [ ] Add examples: house, tree, vehicle, terrain
- [ ] Performance optimization guide for large structures

---

## Implementation Priority

**High Priority (MVP):**
1. Core StructureForm class with basic properties
2. NBT loading from vanilla structure blocks
3. Basic renderer with chunk meshing
4. LOD Level 0 (full detail rendering)
5. Transform support (position, rotation, scale)
6. Basic UI panel with structure picker

**Medium Priority:**
1. LOD Level 1 & 2 (simplified mesh + billboard)
2. Memory management (lazy loading, caching)
3. Performance warnings and limits
4. Assembly animation system
5. Keyframe tracks for all properties

**Low Priority:**
1. Chunk streaming for huge structures
2. Additional format support (Litematica, WorldEdit)
3. Custom structure creator UI
4. Advanced effects (lighting, transparency)
5. Procedural generation

---

## Notes

- **Frustum culling**: REMOVED - too complex for initial implementation
- **Chunk meshing** is highest priority optimization (biggest performance gain)
- Start with vanilla `.nbt` only, add other formats later
- LOD system is critical for weak PC support
- Keep transform API simple (whole structure only, no per-block)
