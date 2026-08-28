/*
 * Bendable Cuboids Animation Tools for Blockbench
 *
 * Companion plugin for GeckoLib Animation Utils. It adds PAL's `bend` channel
 * to GeckoLib animation projects and previews the bend by tessellating the
 * selected bone's cubes in the viewport. The exported animation remains a
 * standard GeckoLib animation JSON with an extra `bend` channel that PAL and
 * BendableCuboids understand.
 */
(function () {
    'use strict';

    const PLUGIN_ID = 'mxt_bendable_cuboids_animation';
    const CHANNEL = 'bend';
    const PERSISTENCE_KEY = 'mxt_bendable_cuboids';
    const PERSISTENCE_VERSION = 1;
    // Blockbench's local Y/Z orientation is mirrored relative to the player
    // model coordinate system used by BendableCuboids at runtime.
    const RUNTIME_BEND_SIGN = -1;
    // This is a viewport approximation, not the runtime renderer. Keep it
    // bounded so a complex Blockbench model remains responsive.
    const SUBDIVISION_LIMIT = 8;
    const geometryStates = new WeakMap();
    const transformStates = new WeakMap();
    let previewEnabled;
    let insertKeyframeAction;

    function cloneJson(value) {
        return JSON.parse(JSON.stringify(value));
    }

    function isObject(value) {
        return value !== null && typeof value === 'object' && !Array.isArray(value);
    }

    function normalizePersistedKeyframe(keyframe) {
        if (!isObject(keyframe) || !Array.isArray(keyframe.data_points)) return null;
        const copy = cloneJson(keyframe);
        copy.channel = CHANNEL;
        copy.data_points.forEach(dataPoint => {
            if (!isObject(dataPoint)) return;
            if (dataPoint.x === undefined || dataPoint.x === '') dataPoint.x = '0';
            dataPoint.y = '0';
            dataPoint.z = '0';
        });
        return copy;
    }

    function restorePersistedBends(event) {
        const model = event.model;
        const persisted = model?.[PERSISTENCE_KEY];
        if (!isObject(persisted) || !Array.isArray(persisted.animations) || !Array.isArray(model.animations)) return;

        for (const storedAnimation of persisted.animations) {
            if (!isObject(storedAnimation) || !isObject(storedAnimation.animators)) continue;
            const animation = model.animations.find(candidate => candidate.uuid === storedAnimation.uuid) ??
                model.animations.find(candidate => candidate.name === storedAnimation.name);
            if (!isObject(animation)) continue;
            animation.animators ??= {};

            for (const [animatorId, storedKeyframes] of Object.entries(storedAnimation.animators)) {
                if (!Array.isArray(storedKeyframes)) continue;
                const animator = animation.animators[animatorId] ??= {type: 'bone', keyframes: []};
                const restored = storedKeyframes.map(normalizePersistedKeyframe).filter(Boolean);
                if (!restored.length) continue;
                const otherKeyframes = Array.isArray(animator.keyframes)
                    ? animator.keyframes.filter(keyframe => keyframe.channel !== CHANNEL)
                    : [];
                animator.keyframes = otherKeyframes.concat(restored);
            }
        }
    }

    function persistBends(event) {
        const model = event.model;
        if (!Array.isArray(model?.animations)) return;

        const animations = [];
        for (const animation of model.animations) {
            if (!isObject(animation) || !isObject(animation.animators)) continue;
            const animators = {};
            for (const [animatorId, animator] of Object.entries(animation.animators)) {
                if (!isObject(animator) || !Array.isArray(animator.keyframes)) continue;
                const bends = animator.keyframes.filter(keyframe => keyframe.channel === CHANNEL);
                if (!bends.length) continue;
                animators[animatorId] = cloneJson(bends);
                // Keep the custom track in one well-defined root field. This
                // prevents Blockbench from discarding it if channel handling
                // changes before the next time the project is opened.
                animator.keyframes = animator.keyframes.filter(keyframe => keyframe.channel !== CHANNEL);
            }
            if (Object.keys(animators).length) {
                animations.push({uuid: animation.uuid, name: animation.name, animators});
            }
        }

        if (animations.length) {
            model[PERSISTENCE_KEY] = {version: PERSISTENCE_VERSION, animations};
        } else {
            delete model[PERSISTENCE_KEY];
        }
    }

    function normalizeBendKeyframe(keyframe) {
        if (keyframe.channel !== CHANNEL) return;
        keyframe.data_points.forEach(dataPoint => {
            if (dataPoint.x === undefined || dataPoint.x === '') dataPoint.x = '0';
            // PAL/BendableCuboids has one scalar bend value. Keep GeckoLib's
            // vector encoding valid without exposing unused axes to authors.
            dataPoint.y = '0';
            dataPoint.z = '0';
        });
    }

    function normalizeAllBendKeyframes() {
        Animator.animations.forEach(animation => {
            Object.values(animation.animators).forEach(animator => {
                if (!(animator instanceof BoneAnimator) || !Array.isArray(animator[CHANNEL])) return;
                animator[CHANNEL].forEach(normalizeBendKeyframe);
            });
        });
    }

    function updateBendKeyframePanel() {
        const selected = Timeline.selected || [];
        const isBendSelection = selected.length && selected.every(keyframe => keyframe.channel === CHANNEL);

        document.querySelectorAll('#keyframe_bar_y, #keyframe_bar_z').forEach(element => {
            element.style.display = isBendSelection ? 'none' : '';
        });
        document.querySelectorAll('#keyframe_bar_x > label').forEach(label => {
            label.textContent = isBendSelection ? '旋转角度' : 'X';
            label.title = isBendSelection ? 'BendableCuboids 弯曲角度（度）' : '';
        });
        if (isBendSelection) selected.forEach(normalizeBendKeyframe);
    }

    function isAnimationProject() {
        return typeof Animator !== 'undefined' && Animator.open;
    }

    function isEnabled() {
        return isAnimationProject() && previewEnabled && previewEnabled.value;
    }

    function toNumber(value) {
        const number = typeof value === 'number' ? value : Number(value);
        return Number.isFinite(number) ? number : 0;
    }

    function getBendDegrees(animator) {
        const value = animator.interpolate(CHANNEL, true);
        if (Array.isArray(value)) return toNumber(value[0]);
        if (value && typeof value === 'object') return toNumber(value.x ?? value[0]);
        return toNumber(value);
    }

    function collectDirectCubes(group) {
        // A BendableCuboid belongs to one ModelPart. Child ModelParts (for
        // example a held item below right_arm) inherit bone transforms but are
        // not themselves bent by BendableCuboids.
        return Array.isArray(group.children) ? group.children.filter(child => child instanceof Cube) : [];
    }

    function isHeldItemBone(group) {
        const name = String(group.name ?? '').toLowerCase().replace(/[ _-]/g, '');
        return name === 'rightitem' || name === 'leftitem';
    }

    function collectHeldItemBones(group) {
        // The player model convention keeps right_item/left_item directly
        // below the arm. Restricting this to direct children avoids changing
        // arbitrary decorative child bones while a limb is bent.
        return Array.isArray(group.children)
            ? group.children.filter(child => child instanceof Group && isHeldItemBone(child))
            : [];
    }

    function getBendPivot(group) {
        const cubes = collectDirectCubes(group);
        const pivot = new THREE.Vector3();
        let count = 0;

        for (const cube of cubes) {
            const mesh = cube.mesh;
            if (!mesh || !mesh.geometry) continue;
            const source = getGeometryState(mesh).source;
            const positions = source.getAttribute('position');
            if (!positions || positions.count === 0) continue;

            // Geometry positions and mesh offsets are both expressed in the
            // arm's local space. Their combined centre is BendableCuboids'
            // actual bend point, independent of how this BB model chose its
            // bone origins.
            const bounds = getBounds(positions);
            pivot.add(new THREE.Vector3(
                (bounds.minX + bounds.maxX) / 2 + mesh.position.x,
                (bounds.minY + bounds.maxY) / 2 + mesh.position.y,
                (bounds.minZ + bounds.maxZ) / 2 + mesh.position.z
            ));
            count++;
        }
        return count ? pivot.multiplyScalar(1 / count) : new THREE.Vector3();
    }

    function getBounds(positions) {
        const bounds = {
            minX: Infinity, minY: Infinity, minZ: Infinity,
            maxX: -Infinity, maxY: -Infinity, maxZ: -Infinity
        };
        for (let index = 0; index < positions.count; index++) {
            const x = positions.getX(index);
            const y = positions.getY(index);
            const z = positions.getZ(index);
            bounds.minX = Math.min(bounds.minX, x);
            bounds.minY = Math.min(bounds.minY, y);
            bounds.minZ = Math.min(bounds.minZ, z);
            bounds.maxX = Math.max(bounds.maxX, x);
            bounds.maxY = Math.max(bounds.maxY, y);
            bounds.maxZ = Math.max(bounds.maxZ, z);
        }
        return bounds;
    }

    function lerpPoint(a, b, amount) {
        return {
            x: a.x + (b.x - a.x) * amount,
            y: a.y + (b.y - a.y) * amount,
            z: a.z + (b.z - a.z) * amount,
            u: a.u + (b.u - a.u) * amount,
            v: a.v + (b.v - a.v) * amount
        };
    }

    function bilerpPoint(a, b, c, d, u, v) {
        return lerpPoint(lerpPoint(a, b, u), lerpPoint(d, c, u), v);
    }

    // This ports BendableCuboids' default non-inverted player bend behaviour.
    // The caller converts the animation angle into Blockbench coordinates.
    function bendPoint(point, bounds, radians) {
        if (Math.abs(radians) < 0.0001) return point;

        const height = bounds.maxY - bounds.minY;
        const extentZ = (Math.abs(bounds.minZ) + Math.abs(bounds.maxZ)) / 2;
        if (height < 0.0001 || extentZ < 0.0001) return point;

        let x = point.x;
        let y = point.y;
        let z = point.z;
        let distanceFromBase = Math.abs(bounds.maxY - y);
        let distanceFromOther = Math.abs(bounds.minY - y);

        // BendableCuboids' default player setup is not inverted, which swaps
        // the planes before moving the lower half around the centre pivot.
        [distanceFromBase, distanceFromOther] = [distanceFromOther, distanceFromBase];

        const centreX = (bounds.minX + bounds.maxX) / 2;
        const centreY = (bounds.minY + bounds.maxY) / 2;
        const centreZ = (bounds.minZ + bounds.maxZ) / 2;
        const halfHeight = height / 2;
        const shear = Math.tan(radians / 2) * (z * 2 / extentZ);
        const threshold = halfHeight - (shear >= 0 ? Math.min(Math.abs(shear) / 2, 1) : Math.abs(shear));
        const overNinety = Math.abs(((radians + Math.PI) % (Math.PI * 2)) - Math.PI) > Math.PI / 2;

        if (distanceFromBase < distanceFromOther) {
            if (!overNinety && distanceFromBase + distanceFromOther <= height && distanceFromBase > threshold) {
                y = centreY + shear;
            }
            const relativeY = y - centreY;
            const relativeZ = z - centreZ;
            y = centreY + relativeY * Math.cos(radians) - relativeZ * Math.sin(radians);
            z = centreZ + relativeY * Math.sin(radians) + relativeZ * Math.cos(radians);
        } else if (!overNinety && distanceFromBase + distanceFromOther <= height && distanceFromOther > threshold) {
            y = centreY - shear;
        }

        return {x, y, z, u: point.u, v: point.v};
    }

    function materialIndexForFace(geometry, indexOffset) {
        const group = geometry.groups.find(entry => indexOffset >= entry.start && indexOffset < entry.start + entry.count);
        return group ? group.materialIndex : 0;
    }

    function buildBentGeometry(source, degrees) {
        const positions = source.getAttribute('position');
        const uvs = source.getAttribute('uv');
        if (!positions || !uvs) return null;

        const radians = degrees * Math.PI / 180;
        const bounds = getBounds(positions);
        const outputPositions = [];
        const outputUvs = [];
        const indices = [];
        const groups = [];
        let vertexCount = 0;
        let indexCount = 0;

        const index = source.getIndex();
        const faceCount = index ? Math.floor(index.count / 6) : Math.floor(positions.count / 4);
        if (faceCount === 0) return null;

        for (let faceIndex = 0; faceIndex < faceCount; faceIndex++) {
            const indexOffset = faceIndex * 6;
            // Blockbench cubes have eight shared vertices. Each face is two
            // triangles encoded as [0, 2, 1, 2, 3, 1], so reconstruct its
            // four corners before tessellating it.
            const vertexIndices = index
                ? [index.getX(indexOffset), index.getX(indexOffset + 2), index.getX(indexOffset + 1), index.getX(indexOffset + 4)]
                : [faceIndex * 4, faceIndex * 4 + 1, faceIndex * 4 + 2, faceIndex * 4 + 3];
            const corners = [];
            for (let corner = 0; corner < 4; corner++) {
                const vertexIndex = vertexIndices[corner];
                // Positions are shared between cube faces, while Blockbench
                // stores four independent UV entries per face.
                const uvIndex = faceIndex * 4 + corner;
                corners.push({
                    x: positions.getX(vertexIndex), y: positions.getY(vertexIndex), z: positions.getZ(vertexIndex),
                    u: uvs.getX(uvIndex), v: uvs.getY(uvIndex)
                });
            }

            const yExtent = Math.max(...corners.map(point => point.y)) - Math.min(...corners.map(point => point.y));
            const zExtent = Math.max(...corners.map(point => point.z)) - Math.min(...corners.map(point => point.z));
            const verticalSegments = Math.max(1, Math.min(SUBDIVISION_LIMIT, Math.ceil(yExtent)));
            const horizontalSegments = Math.max(1, Math.min(SUBDIVISION_LIMIT, Math.ceil(zExtent)));
            const groupStart = indexCount;

            for (let ySegment = 0; ySegment < verticalSegments; ySegment++) {
                for (let xSegment = 0; xSegment < horizontalSegments; xSegment++) {
                    const u0 = xSegment / horizontalSegments;
                    const u1 = (xSegment + 1) / horizontalSegments;
                    const v0 = ySegment / verticalSegments;
                    const v1 = (ySegment + 1) / verticalSegments;
                    const cell = [
                        // Blockbench stores corners as top-left, top-right,
                        // bottom-left, bottom-right. bilerpPoint expects its
                        // final two arguments in bottom-right, bottom-left
                        // order, otherwise every tessellated cell crosses.
                        bilerpPoint(corners[0], corners[1], corners[3], corners[2], u0, v0),
                        bilerpPoint(corners[0], corners[1], corners[3], corners[2], u1, v0),
                        bilerpPoint(corners[0], corners[1], corners[3], corners[2], u0, v1),
                        bilerpPoint(corners[0], corners[1], corners[3], corners[2], u1, v1)
                    ].map(point => bendPoint(point, bounds, radians));

                    for (const point of cell) {
                        outputPositions.push(point.x, point.y, point.z);
                        outputUvs.push(point.u, point.v);
                    }
                    indices.push(vertexCount, vertexCount + 2, vertexCount + 1, vertexCount + 2, vertexCount + 3, vertexCount + 1);
                    vertexCount += 4;
                    indexCount += 6;
                }
            }
            groups.push({start: groupStart, count: indexCount - groupStart, materialIndex: materialIndexForFace(source, indexOffset)});
        }

        const geometry = new THREE.BufferGeometry();
        geometry.setAttribute('position', new THREE.Float32BufferAttribute(outputPositions, 3));
        geometry.setAttribute('uv', new THREE.Float32BufferAttribute(outputUvs, 2));
        geometry.setIndex(indices);
        groups.forEach(group => geometry.addGroup(group.start, group.count, group.materialIndex));
        geometry.computeVertexNormals();
        geometry.computeBoundingBox();
        geometry.computeBoundingSphere();
        return geometry;
    }

    function restoreMesh(mesh) {
        const state = geometryStates.get(mesh);
        if (!state || mesh.geometry === state.source) return;
        // Only dispose a geometry owned by this plugin. A cube edit may have
        // replaced the mesh geometry while an animation was playing.
        if (mesh.geometry === state.preview) mesh.geometry.dispose();
        mesh.geometry = state.source;
        state.preview = null;
    }

    function restoreAllMeshes() {
        Cube.all.forEach(cube => {
            if (cube.mesh) restoreMesh(cube.mesh);
        });
        Group.all.forEach(group => {
            if (group.mesh) restoreTransform(group.mesh);
        });
    }

    function vectorEquals(a, b) {
        return a.distanceToSquared(b) < 0.00000001;
    }

    function quaternionEquals(a, b) {
        // Quaternions q and -q describe the same rotation.
        return Math.abs(a.dot(b)) > 0.99999999;
    }

    function getTransformState(object) {
        let state = transformStates.get(object);
        if (!state) {
            state = {
                sourcePosition: object.position.clone(),
                sourceQuaternion: object.quaternion.clone(),
                previewPosition: null,
                previewQuaternion: null
            };
            transformStates.set(object, state);
        } else if (!state.previewPosition ||
            !vectorEquals(object.position, state.previewPosition) ||
            !quaternionEquals(object.quaternion, state.previewQuaternion)) {
            // A regular animation frame or model edit updated this bone after
            // the last bend preview. Treat that as the new source transform.
            state.sourcePosition.copy(object.position);
            state.sourceQuaternion.copy(object.quaternion);
            state.previewPosition = null;
            state.previewQuaternion = null;
        }
        return state;
    }

    function restoreTransform(object) {
        const state = transformStates.get(object);
        if (!state || !state.previewPosition) return;
        object.position.copy(state.sourcePosition);
        object.quaternion.copy(state.sourceQuaternion);
        object.updateMatrixWorld();
        state.previewPosition = null;
        state.previewQuaternion = null;
    }

    function previewHeldItemTransform(itemBone, pivot, radians) {
        const object = itemBone.mesh;
        if (!object) return;

        const state = getTransformState(object);
        restoreTransform(object);
        if (Math.abs(radians) < 0.0001) return;

        // Match the runtime item layer in arm-local space. The layer rotates
        // the held stack about the arm's bend centre; deriving that centre
        // from the mesh avoids treating a child item's hand-origin as though
        // it were the arm origin. The item remains completely rigid.
        const rotation = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1, 0, 0), radians);
        const position = state.sourcePosition.clone().sub(pivot).applyQuaternion(rotation).add(pivot);
        const quaternion = rotation.multiply(state.sourceQuaternion.clone());

        object.position.copy(position);
        object.quaternion.copy(quaternion);
        object.updateMatrixWorld();
        state.previewPosition = position.clone();
        state.previewQuaternion = quaternion.clone();
    }

    function getGeometryState(mesh) {
        let state = geometryStates.get(mesh);
        if (!state) {
            state = {source: mesh.geometry, preview: null};
            geometryStates.set(mesh, state);
        } else if (mesh.geometry !== state.source && mesh.geometry !== state.preview) {
            // Blockbench regenerated the cube after a model edit. Replace the
            // cached source instead of trying to tessellate stale data.
            state.source = mesh.geometry;
            state.preview = null;
        }
        return state;
    }

    function previewBend(animator, multiplier) {
        if (!isEnabled()) return;
        const group = animator.getGroup();
        if (!group) return;

        const cubes = collectDirectCubes(group);
        const heldItemBones = collectHeldItemBones(group);

        // Blockbench emits display_animation_frame before the browser draws.
        // Restoring in that event made the bend invisible. Instead restore the
        // previous preview here, at the start of the next animator update.
        cubes.forEach(cube => {
            const mesh = cube.mesh;
            if (!mesh || !mesh.geometry) return;
            getGeometryState(mesh);
            restoreMesh(mesh);
        });
        heldItemBones.forEach(itemBone => {
            if (itemBone.mesh) {
                getTransformState(itemBone.mesh);
                restoreTransform(itemBone.mesh);
            }
        });

        // A positive PAL bend is applied around the opposite local X axis in
        // Blockbench. Convert only for the viewport: persisted and exported
        // keyframe values remain the runtime PAL values.
        const degrees = getBendDegrees(animator) * multiplier * RUNTIME_BEND_SIGN;
        if (Math.abs(degrees) < 0.0001) return;
        const radians = degrees * Math.PI / 180;
        const pivot = getBendPivot(group);

        cubes.forEach(cube => {
            const mesh = cube.mesh;
            if (!mesh || !mesh.geometry) return;
            const state = getGeometryState(mesh);
            const bentGeometry = buildBentGeometry(state.source, degrees);
            if (!bentGeometry) return;
            mesh.geometry = bentGeometry;
            state.preview = bentGeometry;
        });
        heldItemBones.forEach(itemBone => previewHeldItemTransform(itemBone, pivot, radians));
    }

    function addChannel() {
        if (!BoneAnimator.prototype.channels[CHANNEL]) {
            BoneAnimator.addChannel(CHANNEL, {
                name: 'Bend (PAL)',
                mutable: true,
                transform: true,
                max_data_points: 2,
                // A bend preview only needs a bone animator and cube meshes.
                // Keeping this format-agnostic also lets an existing model_item
                // project be animated before it is exported as GeckoLib JSON.
                condition: () => isAnimationProject(),
                displayFrame: previewBend
            });
        } else {
            BoneAnimator.prototype.channels[CHANNEL].displayFrame = previewBend;
        }
        Animator.possible_channels[CHANNEL] = BoneAnimator.prototype.channels[CHANNEL];
        // Existing projects may have been opened before this companion plugin.
        // BoneAnimator creates its channel arrays only in its constructor, so
        // make the newly registered channel available to those animators too.
        Animator.animations.forEach(animation => {
            Object.values(animation.animators).forEach(animator => {
                if (animator instanceof BoneAnimator && !Array.isArray(animator[CHANNEL])) {
                    animator[CHANNEL] = [];
                }
            });
        });
        normalizeAllBendKeyframes();
    }

    function insertBendKeyframe() {
        const animator = Timeline.selected_animator;
        if (!animator || animator.type !== 'bone') {
            Blockbench.showQuickMessage('Select a bone in the animation timeline first.');
            return;
        }
        animator[CHANNEL] ??= [];
        const existing = animator[CHANNEL].find(keyframe => Math.epsilon(keyframe.time, Timeline.time, 0.0001));
        if (existing) {
            existing.select();
            return;
        }
        const keyframe = animator.createKeyframe({x: 0, y: 0, z: 0}, Timeline.time, CHANNEL, true, true);
        normalizeBendKeyframe(keyframe);
        keyframe.select();
        Animator.preview();
    }

    BBPlugin.register(PLUGIN_ID, {
        title: 'Bendable Cuboids Animation Tools',
        author: 'IAFEnvoy',
        description: 'Adds PAL/BendableCuboids bend keyframes and viewport preview to GeckoLib Animation Utils.',
        icon: 'accessibility_new',
        variant: 'both',
        min_version: '5.0.0',
        onload() {
            previewEnabled = new Setting('mxt_bendable_cuboids_preview', {
                name: 'Preview PAL bendable cuboids',
                description: 'Tessellate and bend cubes in the viewport while previewing GeckoLib animations.',
                category: 'edit',
                value: true,
                onChange: restoreAllMeshes
            });
            addChannel();
            // Project codec callbacks run around .bbmodel serialization. The
            // parse callback is early enough to restore keyframes before
            // Blockbench instantiates its Animation and BoneAnimator objects.
            Codecs.project.on('parse', restorePersistedBends);
            Codecs.project.on('compile', persistBends);
            Blockbench.on('update_keyframe_selection', updateBendKeyframePanel);
            insertKeyframeAction = new Action('mxt_insert_bend_keyframe', {
                name: 'Insert PAL Bend Keyframe',
                description: 'Create a bend keyframe. Edit only its X value; Y and Z are ignored by BendableCuboids.',
                icon: 'rotate_right',
                category: 'animation',
                condition: () => isAnimationProject(),
                click: insertBendKeyframe
            });
            MenuBar.addAction(insertKeyframeAction, 'animation');
        },
        onunload() {
            restoreAllMeshes();
            Codecs.project.removeListener('parse', restorePersistedBends);
            Codecs.project.removeListener('compile', persistBends);
            Blockbench.removeListener('update_keyframe_selection', updateBendKeyframePanel);
            if (insertKeyframeAction) insertKeyframeAction.delete();
            if (previewEnabled) previewEnabled.delete();
            Animator.animations.forEach(animation => {
                Object.values(animation.animators).forEach(animator => {
                    if (animator instanceof BoneAnimator) delete animator[CHANNEL];
                });
            });
            delete Animator.possible_channels[CHANNEL];
            delete BoneAnimator.prototype.channels[CHANNEL];
        }
    });
})();
