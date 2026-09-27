#!/usr/bin/env python3
"""Build Atmosynq's deterministic production GLB scene.

The source is code rather than a checked-in binary so GitHub Actions can generate
exactly the same embedded PBR scene for every APK. The resulting GLB contains the
environment kit that Filament selectively composes for city / town / terrain /
latitude profiles.
"""

import argparse
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter
import trimesh
from trimesh.visual.material import PBRMaterial
from trimesh.visual.texture import TextureVisuals


def noise_tex(size, base, variation, streak=False):
    arr = np.zeros((size, size, 4), dtype=np.uint8)
    noise = np.random.normal(0, 1, (size, size))
    for y in range(size):
        for x in range(size):
            n = noise[y, x]
            if streak:
                n += 0.5 * math.sin((x + y * 0.15) * 0.18)
            rgb = np.clip(np.array(base) + n * variation, 0, 255)
            arr[y, x, :3] = rgb
            arr[y, x, 3] = 255
    return Image.fromarray(arr, "RGBA").filter(
        ImageFilter.GaussianBlur(radius=1.2)
    )


def normal_tex(size, strength):
    h = np.random.normal(0, 1, (size, size)).astype(np.float32)
    normalized = np.uint8(
        np.clip((h - h.min()) / (h.max() - h.min()) * 255, 0, 255)
    )
    h = np.asarray(
        Image.fromarray(normalized, "L").filter(
            ImageFilter.GaussianBlur(radius=1.5)
        ),
        dtype=np.float32,
    ) / 255.0
    gy, gx = np.gradient(h)
    nx = -gx * strength
    ny = -gy * strength
    nz = np.ones_like(h)
    norm = np.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6
    rgb = np.stack(
        [
            (nx / norm * 0.5 + 0.5) * 255,
            (ny / norm * 0.5 + 0.5) * 255,
            (nz / norm * 0.5 + 0.5) * 255,
        ],
        axis=-1,
    ).astype(np.uint8)
    alpha = np.full((size, size, 1), 255, dtype=np.uint8)
    return Image.fromarray(np.concatenate([rgb, alpha], axis=-1), "RGBA")


def box(size, center=(0, 0, 0)):
    mesh = trimesh.creation.box(extents=size)
    mesh.apply_translation(center)
    return mesh


def cylinder(radius, height, sections=20):
    mesh = trimesh.creation.cylinder(
        radius=radius,
        height=height,
        sections=sections,
    )
    mesh.apply_transform(
        trimesh.transformations.rotation_matrix(math.pi / 2, [1, 0, 0])
    )
    return mesh


def sphere(radius, subdivisions=2):
    return trimesh.creation.icosphere(
        subdivisions=subdivisions,
        radius=radius,
    )


def cone(radius, height, sections=24):
    mesh = trimesh.creation.cone(
        radius=radius,
        height=height,
        sections=sections,
    )
    mesh.apply_transform(
        trimesh.transformations.rotation_matrix(math.pi / 2, [1, 0, 0])
    )
    return mesh


def roof_mesh(width, depth, height):
    vertices = np.array(
        [
            [-width / 2, 0, -depth / 2],
            [width / 2, 0, -depth / 2],
            [width / 2, 0, depth / 2],
            [-width / 2, 0, depth / 2],
            [0, height, -depth / 2],
            [0, height, depth / 2],
        ],
        dtype=float,
    )
    faces = np.array(
        [
            [0, 1, 4],
            [3, 5, 2],
            [0, 4, 5],
            [0, 5, 3],
            [1, 2, 5],
            [1, 5, 4],
        ],
        dtype=int,
    )
    return trimesh.Trimesh(
        vertices=vertices,
        faces=faces,
        process=True,
    )


def ridge_mesh(material, width, depth, height, segments, rows, seed):
    rng = np.random.default_rng(seed)
    xs = np.linspace(-width / 2, width / 2, segments)
    zs = np.linspace(-depth / 2, depth / 2, rows)
    vertices = []
    for z in zs:
        for x in xs:
            envelope = (1 - abs(x) / (width / 2)) ** 0.65
            profile = (
                0.62
                + 0.22 * math.sin(x * 0.74 + seed)
                + 0.12 * math.sin(x * 1.73 + seed * 0.3)
            )
            row_fade = (
                (1 - abs(z) / (depth / 2 + 1e-3)) * 0.24 + 0.76
            )
            y = max(
                0,
                height * envelope * profile * row_fade
                + rng.normal(0, 0.045),
            )
            vertices.append([x, y, z])

    faces = []
    for row in range(rows - 1):
        for column in range(segments - 1):
            i = row * segments + column
            faces.extend(
                [
                    [i, i + segments, i + 1],
                    [i + 1, i + segments, i + segments + 1],
                ]
            )

    mesh = trimesh.Trimesh(
        vertices=np.array(vertices),
        faces=np.array(faces),
        process=True,
    )
    uv = np.array(
        [
            [(vertex[0] / width) + 0.5, (vertex[2] / depth) + 0.5]
            for vertex in mesh.vertices
        ],
        dtype=np.float32,
    )
    mesh.visual = TextureVisuals(uv=uv, material=material)
    return mesh


def build_scene(output: Path):
    random.seed(42027)
    np.random.seed(42027)

    asphalt = noise_tex(256, (28, 34, 42), 18, streak=True)
    asphalt_normal = normal_tex(256, 10)
    concrete = noise_tex(256, (118, 127, 138), 14)
    concrete_normal = normal_tex(256, 5)
    terrain = noise_tex(256, (35, 67, 58), 20)
    terrain_normal = normal_tex(256, 9)
    roof = noise_tex(256, (48, 54, 66), 13, streak=True)
    water = noise_tex(256, (14, 66, 92), 8, streak=True)
    water_normal = normal_tex(256, 4)
    cloud = noise_tex(256, (196, 210, 224), 18)

    materials = {
        "asphalt": PBRMaterial(
            name="WetAsphalt",
            baseColorFactor=[52, 58, 68, 255],
            roughnessFactor=0.22,
            metallicFactor=0.0,
            baseColorTexture=asphalt,
            normalTexture=asphalt_normal,
        ),
        "concrete": PBRMaterial(
            name="Concrete",
            baseColorFactor=[170, 174, 178, 255],
            roughnessFactor=0.72,
            metallicFactor=0.0,
            baseColorTexture=concrete,
            normalTexture=concrete_normal,
        ),
        "terrain": PBRMaterial(
            name="Terrain",
            baseColorFactor=[76, 101, 84, 255],
            roughnessFactor=0.86,
            metallicFactor=0.0,
            baseColorTexture=terrain,
            normalTexture=terrain_normal,
        ),
        "roof": PBRMaterial(
            name="Roof",
            baseColorFactor=[72, 78, 91, 255],
            roughnessFactor=0.62,
            metallicFactor=0.0,
            baseColorTexture=roof,
        ),
        "glass": PBRMaterial(
            name="Glass",
            baseColorFactor=[42, 62, 78, 255],
            roughnessFactor=0.16,
            metallicFactor=0.32,
            alphaMode="OPAQUE",
            doubleSided=True,
        ),
        "window": PBRMaterial(
            name="WindowGlow",
            baseColorFactor=[36, 50, 64, 255],
            roughnessFactor=0.20,
            metallicFactor=0.12,
            emissiveFactor=[0.12, 0.085, 0.035],
        ),
        "neon_cyan": PBRMaterial(
            name="NeonCyan",
            baseColorFactor=[25, 205, 255, 255],
            roughnessFactor=0.10,
            metallicFactor=0.0,
            emissiveFactor=[0.0, 0.75, 1.0],
        ),
        "neon_purple": PBRMaterial(
            name="NeonPurple",
            baseColorFactor=[170, 80, 255, 255],
            roughnessFactor=0.12,
            metallicFactor=0.0,
            emissiveFactor=[0.70, 0.10, 1.0],
        ),
        "water": PBRMaterial(
            name="Water",
            baseColorFactor=[22, 61, 82, 255],
            roughnessFactor=0.16,
            metallicFactor=0.30,
            alphaMode="OPAQUE",
            baseColorTexture=water,
            normalTexture=water_normal,
            doubleSided=True,
        ),
        "trunk": PBRMaterial(
            name="Trunk",
            baseColorFactor=[92, 68, 50, 255],
            roughnessFactor=0.90,
            metallicFactor=0.0,
        ),
        "pine": PBRMaterial(
            name="Pine",
            baseColorFactor=[34, 77, 62, 255],
            roughnessFactor=0.86,
            metallicFactor=0.0,
            doubleSided=True,
        ),
        "leaf": PBRMaterial(
            name="Leaf",
            baseColorFactor=[44, 94, 67, 255],
            roughnessFactor=0.88,
            metallicFactor=0.0,
            doubleSided=True,
        ),
        "palm": PBRMaterial(
            name="PalmLeaf",
            baseColorFactor=[44, 121, 74, 255],
            roughnessFactor=0.82,
            metallicFactor=0.0,
            doubleSided=True,
        ),
        "cloud": PBRMaterial(
            name="Cloud",
            baseColorFactor=[132, 143, 156, 255],
            roughnessFactor=0.98,
            metallicFactor=0.0,
            alphaMode="OPAQUE",
            baseColorTexture=cloud,
            doubleSided=True,
        ),
        "sun": PBRMaterial(
            name="Sun",
            baseColorFactor=[240, 211, 148, 255],
            roughnessFactor=0.18,
            metallicFactor=0.0,
            emissiveFactor=[0.34, 0.24, 0.07],
        ),
        "metal": PBRMaterial(
            name="Metal",
            baseColorFactor=[72, 82, 94, 255],
            roughnessFactor=0.32,
            metallicFactor=0.78,
        ),
        "siding": PBRMaterial(
            name="HouseSiding",
            baseColorFactor=[150, 151, 145, 255],
            roughnessFactor=0.82,
            metallicFactor=0.0,
            baseColorTexture=concrete,
            normalTexture=concrete_normal,
        ),
        "brick": PBRMaterial(
            name="Brick",
            baseColorFactor=[112, 72, 58, 255],
            roughnessFactor=0.90,
            metallicFactor=0.0,
            baseColorTexture=concrete,
            normalTexture=concrete_normal,
        ),
        "stucco": PBRMaterial(
            name="Stucco",
            baseColorFactor=[174, 164, 147, 255],
            roughnessFactor=0.88,
            metallicFactor=0.0,
            baseColorTexture=concrete,
            normalTexture=concrete_normal,
        ),
        "garage": PBRMaterial(
            name="GarageDoor",
            baseColorFactor=[93, 101, 108, 255],
            roughnessFactor=0.66,
            metallicFactor=0.05,
        ),
    }

    scene = trimesh.Scene()

    def add(mesh, name, material=None, transform=None):
        mesh = mesh.copy()
        if material is not None:
            uv = getattr(mesh.visual, "uv", None)
            if uv is not None:
                mesh.visual = TextureVisuals(
                    uv=np.array(uv),
                    material=material,
                )
            else:
                mesh.visual.material = material
        scene.add_geometry(
            mesh,
            node_name=name,
            geom_name=name,
            transform=transform,
        )

    def plane(width, depth, y, z, material, name):
        vertices = np.array(
            [
                [-width / 2, y, z - depth / 2],
                [width / 2, y, z - depth / 2],
                [width / 2, y, z + depth / 2],
                [-width / 2, y, z + depth / 2],
            ],
            dtype=np.float32,
        )
        faces = np.array([[0, 2, 1], [0, 3, 2]], dtype=np.int64)
        uv = np.array([[0, 0], [4, 0], [4, 4], [0, 4]], dtype=np.float32)
        mesh = trimesh.Trimesh(
            vertices=vertices,
            faces=faces,
            process=False,
        )
        mesh.visual = TextureVisuals(uv=uv, material=material)
        scene.add_geometry(mesh, node_name=name, geom_name=name)

    plane(24, 22, -0.18, -2.8, materials["terrain"], "Ground")
    plane(20, 8, -0.12, 1.8, materials["asphalt"], "WetRoad")
    plane(23, 8, -0.08, -8.8, materials["water"], "Water")

    for i, x in enumerate(np.linspace(-7.5, 7.5, 7)):
        lane = box((1.25, 0.012, 0.10), center=(x, -0.09, 1.8))
        lane_material = PBRMaterial(
            name=f"Lane{i}",
            baseColorFactor=[185, 199, 210, 210],
            roughnessFactor=0.45,
            metallicFactor=0.0,
            emissiveFactor=[0.04, 0.05, 0.06],
        )
        add(lane, f"RoadMark{i}", lane_material)

    mountain_specs = [
        (-14, 22, 5.2, 3.2),
        (-11.8, 18, 3.9, 2.8),
        (-9.8, 14, 2.8, 2.4),
    ]
    for i, (z, width, height, depth) in enumerate(mountain_specs):
        mesh = ridge_mesh(
            materials["terrain"],
            width,
            depth,
            height,
            58 if i == 0 else 46,
            11,
            20 + i * 9,
        )
        mesh.apply_translation((0, -0.18, z))
        add(mesh, f"Mountain{i}", materials["terrain"])

    for i, x in enumerate([-6, 0, 6]):
        hill = sphere(3.4 if i == 1 else 3.0, subdivisions=3)
        hill.apply_scale([1.45, 0.36, 1.0])
        hill.apply_translation((x, 0.28, -10.0 + i * 0.25))
        add(hill, f"RollingHill{i}", materials["terrain"])

    def add_building(idx, x, z, width, height, depth, rotation=0.0):
        transform = trimesh.transformations.rotation_matrix(
            rotation,
            [0, 1, 0],
        )
        transform[:3, 3] = [x, 0, z]

        add(
            box((width, height, depth), center=(0, height / 2, 0)),
            f"CityBuilding{idx}",
            materials["concrete"],
            transform,
        )
        add(
            box(
                (width * 0.78, height * 0.78, 0.035),
                center=(0, height * 0.54, depth / 2 + 0.02),
            ),
            f"CityGlass{idx}",
            materials["glass"],
            transform,
        )

        windows = []
        columns = max(2, min(6, int(width / 0.5)))
        rows = max(2, min(10, int(height / 0.75)))
        for row in range(rows):
            for column in range(columns):
                if (idx * 17 + row * 7 + column * 13) % 5 == 0:
                    continue
                wx = -width * 0.34 + (column + 0.5) * (
                    width * 0.68 / columns
                )
                wy = height * 0.15 + (row + 0.5) * (
                    height * 0.68 / rows
                )
                windows.append(
                    box(
                        (
                            width * 0.42 / columns,
                            height * 0.32 / rows,
                            0.025,
                        ),
                        center=(wx, wy, depth / 2 + 0.055),
                    )
                )
        if windows:
            add(
                trimesh.util.concatenate(windows),
                f"CityWindows{idx}",
                materials["window"],
                transform,
            )

    building_index = 0
    for row, z in enumerate([-6.3, -4.8, -3.5]):
        count = 8 if row == 0 else 7
        for column in range(count):
            x = (
                (column - (count - 1) / 2) * 2.05
                + (row % 2) * 0.55
            )
            height = (
                2.0
                + ((building_index * 37) % 9) * 0.42
                + (
                    1.0
                    if row == 0 and column in (2, 3, 4)
                    else 0.0
                )
            )
            width = 1.15 + ((building_index * 11) % 5) * 0.15
            depth = 1.0 + ((building_index * 7) % 4) * 0.17
            add_building(
                building_index,
                x,
                z,
                width,
                height,
                depth,
                ((building_index % 3) - 1) * 0.035,
            )
            building_index += 1

    def add_house(index, x, z, scale=1.0, rotation=0.0):
        variant = index % 4
        width = (1.45 + variant * 0.12) * scale
        depth = (1.15 + (index % 3) * 0.14) * scale
        stories = 2 if index % 5 in (1, 4) else 1
        height = (0.76 + (stories - 1) * 0.48) * scale
        transform = trimesh.transformations.rotation_matrix(
            rotation,
            [0, 1, 0],
        )
        transform[:3, 3] = [x, 0, z]

        facade_material = [
            materials["siding"],
            materials["brick"],
            materials["stucco"],
            materials["siding"],
        ][variant]

        add(
            box((width, height, depth), center=(0, height / 2, 0)),
            f"House{index}",
            facade_material,
            transform,
        )

        roof_height = (0.34 + (index % 3) * 0.08) * scale
        roof_geometry = roof_mesh(
            width * 1.10,
            depth * 1.13,
            roof_height,
        )
        roof_geometry.apply_translation((0, height, 0))
        add(
            roof_geometry,
            f"HouseRoof{index}",
            materials["roof"],
            transform,
        )

        # Attached garage / porch mass breaks the copy-pasted-box silhouette.
        garage_width = width * (0.34 if index % 2 == 0 else 0.26)
        garage_height = height * 0.46
        garage_x = width * (0.24 if index % 2 == 0 else -0.25)
        garage = box(
            (garage_width, garage_height, 0.11 * scale),
            center=(
                garage_x,
                garage_height / 2,
                depth / 2 + 0.06 * scale,
            ),
        )
        add(
            garage,
            f"HouseGarage{index}",
            materials["garage"],
            transform,
        )

        door = box(
            (0.20 * scale, 0.42 * scale, 0.035 * scale),
            center=(
                -width * 0.18,
                0.21 * scale,
                depth / 2 + 0.035 * scale,
            ),
        )
        add(
            door,
            f"HouseDoor{index}",
            materials["roof"],
            transform,
        )

        windows = []
        rows = stories
        for row in range(rows):
            window_y = (
                0.42 * scale +
                row * 0.47 * scale
            )
            for side in (-0.26, 0.26):
                windows.append(
                    box(
                        (
                            0.20 * scale,
                            0.20 * scale,
                            0.022 * scale,
                        ),
                        center=(
                            side * width,
                            window_y,
                            depth / 2 + 0.025 * scale,
                        ),
                    )
                )
        add(
            trimesh.util.concatenate(windows),
            f"HouseWindows{index}",
            materials["glass"],
            transform,
        )

        if index % 3 == 0:
            chimney = box(
                (
                    0.15 * scale,
                    0.48 * scale,
                    0.17 * scale,
                ),
                center=(
                    width * 0.24,
                    height + roof_height * 0.72,
                    0,
                ),
            )
            add(
                chimney,
                f"HouseChimney{index}",
                materials["brick"],
                transform,
            )

    for i in range(14):
        row = i // 7
        column = i % 7
        add_house(
            i,
            (column - 3) * 2.55 + (0.6 if row else 0),
            -2.9 - row * 2.35,
            0.85 + 0.08 * ((i * 3) % 4),
            ((i % 4) - 1.5) * 0.035,
        )

    def add_pine(index, x, z, scale):
        trunk = cylinder(0.11 * scale, 1.25 * scale, sections=14)
        trunk.apply_translation((x, 0.62 * scale, z))
        add(trunk, f"PineTrunk{index}", materials["trunk"])

        layers = []
        for radius, height, y in [
            (0.74, 1.55, 0.9),
            (0.58, 1.35, 1.55),
            (0.42, 1.10, 2.12),
        ]:
            crown = cone(radius * scale, height * scale, sections=20)
            crown.apply_translation((x, y * scale, z))
            layers.append(crown)
        add(
            trimesh.util.concatenate(layers),
            f"Pine{index}",
            materials["pine"],
        )

    def add_broadleaf(index, x, z, scale):
        trunk = cylinder(0.12 * scale, 1.25 * scale, sections=14)
        trunk.apply_translation((x, 0.62 * scale, z))
        add(trunk, f"BroadleafTrunk{index}", materials["trunk"])

        crowns = []
        for dx, dy, dz, radius in [
            (0, 1.45, 0, 0.56),
            (0.35, 1.55, 0.08, 0.42),
            (-0.34, 1.52, 0.02, 0.44),
            (0.04, 1.82, -0.05, 0.43),
        ]:
            crown = sphere(radius * scale, subdivisions=2)
            crown.apply_translation(
                (x + dx * scale, dy * scale, z + dz * scale)
            )
            crowns.append(crown)
        add(
            trimesh.util.concatenate(crowns),
            f"Broadleaf{index}",
            materials["leaf"],
        )

    def add_palm(index, x, z, scale):
        trunk = cylinder(0.10 * scale, 2.0 * scale, sections=14)
        trunk.apply_translation((x, 1.0 * scale, z))
        add(trunk, f"PalmTrunk{index}", materials["trunk"])

        leaves = []
        for leaf_index in range(8):
            angle = leaf_index * math.tau / 8
            length = 0.92 * scale
            leaf = box(
                (length, 0.025 * scale, 0.16 * scale),
                center=(length / 2, 0, 0),
            )
            rotate_y = trimesh.transformations.rotation_matrix(
                angle,
                [0, 1, 0],
            )
            tilt = trimesh.transformations.rotation_matrix(
                -0.20,
                [0, 0, 1],
            )
            transform = trimesh.transformations.concatenate_matrices(
                rotate_y,
                tilt,
            )
            transform[:3, 3] = [x, 2.0 * scale, z]
            leaf.apply_transform(transform)
            leaves.append(leaf)
        add(
            trimesh.util.concatenate(leaves),
            f"Palm{index}",
            materials["palm"],
        )

    for i in range(22):
        side = -1 if i % 2 == 0 else 1
        add_pine(
            i,
            side * (5.8 + (i % 5) * 0.7),
            2.8 - (i // 2) * 1.05,
            0.82 + 0.09 * (i % 4),
        )

    for i in range(18):
        side = -1 if i % 2 == 0 else 1
        add_broadleaf(
            i,
            side * (5.2 + (i % 4) * 0.9),
            2.0 - (i // 2) * 1.15,
            0.86 + 0.10 * (i % 3),
        )

    for i in range(10):
        side = -1 if i % 2 == 0 else 1
        add_palm(
            i,
            side * (5.8 + (i % 3) * 1.05),
            1.2 - (i // 2) * 1.7,
            0.9 + 0.1 * (i % 2),
        )

    for i, x in enumerate(np.linspace(-7.8, 7.8, 7)):
        pole = cylinder(0.055, 2.2, sections=12)
        pole.apply_translation((x, 1.1, 0.6))
        add(pole, f"StreetPole{i}", materials["metal"])

        light = sphere(0.12, subdivisions=2)
        light.apply_translation((x, 2.18, 0.6))
        add(light, f"StreetLight{i}", materials["window"])

    cloud_centers = [
        (-5.8, 6.6, -12.5),
        (-1.8, 7.4, -15.0),
        (4.4, 6.9, -13.8),
        (-3.2, 5.8, -10.8),
        (3.1, 5.6, -10.4),
    ]
    for i, (cx, cy, cz) in enumerate(cloud_centers):
        blobs = []
        for j in range(12):
            angle = j * math.tau / 12
            radius = 0.34 + 0.10 * ((j * 7 + i * 3) % 4)
            blob = sphere(radius, subdivisions=2)
            blob.apply_scale([1.80, 0.34, 1.22])
            blob.apply_translation(
                (
                    cx + math.cos(angle) * 1.10,
                    cy + math.sin(angle * 1.9) * 0.14,
                    cz + math.sin(angle) * 0.38,
                )
            )
            blobs.append(blob)
        add(
            trimesh.util.concatenate(blobs),
            f"Cloud{i}",
            materials["cloud"],
        )

    sun = sphere(0.48, subdivisions=3)
    sun.apply_translation((5.4, 6.7, -11.0))
    add(sun, "Sun", materials["sun"])

    for name, material, y, phase in [
        ("RibbonCyan", materials["neon_cyan"], 3.8, 0.0),
        ("RibbonPurple", materials["neon_purple"], 3.35, 1.2),
    ]:
        segments = []
        points = []
        for i in range(22):
            x = -7.2 + i * (14.4 / 21)
            z = -1.5 + i * (-3.5 / 21)
            yy = y + 0.18 * math.sin(i * 0.55 + phase)
            points.append(np.array([x, yy, z]))
        for start, end in zip(points[:-1], points[1:]):
            vector = end - start
            length = np.linalg.norm(vector)
            segment = trimesh.creation.cylinder(
                radius=0.025,
                height=length,
                sections=10,
            )
            align = trimesh.geometry.align_vectors(
                [0, 0, 1],
                vector / length,
            )
            segment.apply_transform(align)
            segment.apply_translation((start + end) / 2)
            segments.append(segment)
        add(
            trimesh.util.concatenate(segments),
            name,
            material,
        )

    for geometry in scene.geometry.values():
        # All generator primitives already carry valid winding/normals. Avoid
        # trimesh.fix_normals() here because that pulls SciPy into CI merely to
        # re-evaluate connected components on meshes we just constructed.
        geometry.remove_unreferenced_vertices()

    blob = scene.export(file_type="glb")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(blob)

    # Re-open it now so CI fails here instead of shipping a corrupt model.
    loaded = trimesh.load(output, force="scene")
    required = {
        "Ground",
        "WetRoad",
        "Water",
        "Mountain0",
        "Mountain1",
        "Mountain2",
        "RollingHill0",
        "CityBuilding0",
        "House0",
        "Pine0",
        "Broadleaf0",
        "Palm0",
        "Cloud0",
        "Cloud1",
        "Cloud2",
        "RibbonCyan",
        "RibbonPurple",
        "Sun",
    }
    names = set(loaded.graph.nodes_geometry)
    missing = sorted(required - names)
    if missing:
        raise RuntimeError(f"Generated GLB is missing required nodes: {missing}")
    if len(blob) < 500_000:
        raise RuntimeError(
            f"Generated scene is unexpectedly tiny: {len(blob)} bytes"
        )

    print(
        f"Generated {output} ({len(blob):,} bytes, "
        f"{len(loaded.geometry)} geometries)"
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--out",
        default="app/src/main/assets/filament/atmos_scene.glb",
    )
    args = parser.parse_args()
    build_scene(Path(args.out))


if __name__ == "__main__":
    main()
