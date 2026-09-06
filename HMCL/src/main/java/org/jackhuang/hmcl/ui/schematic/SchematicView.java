/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.schematic;

import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import org.glavo.monetfx.ColorRole;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A JavaFX 3D view of a schematic with an orbit camera.
///
/// The model is centered at the origin and the camera orbits around it. The supported controls are:
/// <ul>
///   <li>Drag with the primary button to orbit the camera around the structure.</li>
///   <li>Drag with the secondary/middle button, or hold Shift while dragging, to pan.</li>
///   <li>Scroll the mouse wheel to zoom in and out.</li>
///   <li>Arrow keys rotate the camera, <b>+</b>/<b>-</b> keys zoom, and <b>R</b> resets the view.</li>
/// </ul>
/// The view resizes together with its parent; it also tracks the launcher color scheme so that the
/// 3D background stays consistent with the rest of the UI.
@NotNullByDefault
public final class SchematicView extends Region {

    private static final double ORBIT_SENSITIVITY = 0.3;
    private static final double KEYBOARD_ROTATE_STEP = 2.0;
    private static final double ZOOM_STEP = 0.9;
    private static final double MIN_DISTANCE = 1.0;
    private static final double MAX_DISTANCE = 100_000.0;
    private static final double FIELD_OF_VIEW = 60.0;

    private final Group sceneRoot = new Group();
    private final Group modelGroup = new Group();
    private final Group cameraGroup = new Group();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Rotate yaw = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(0, Rotate.X_AXIS);
    private final SubScene subScene;

    private final @Nullable EventHandler<MouseEvent> mousePressedHandler;
    private final @Nullable EventHandler<MouseEvent> mouseDraggedHandler;
    private final @Nullable EventHandler<ScrollEvent> scrollHandler;
    private final @Nullable EventHandler<KeyEvent> keyHandler;

    private double distance = 100;
    private double fitDistance = 100;
    private double centerX;
    private double centerY;
    private double centerZ;
    private double lastX;
    private double lastY;

    /// Creates the schematic view.
    public SchematicView() {
        camera.setNearClip(0.1);
        camera.setFarClip(20_000);
        camera.setFieldOfView(FIELD_OF_VIEW);

        cameraGroup.getTransforms().addAll(yaw, pitch);
        cameraGroup.getChildren().add(camera);
        sceneRoot.getChildren().addAll(modelGroup, createLights());

        subScene = new SubScene(sceneRoot, 200, 200, true, SceneAntialiasing.BALANCED);
        subScene.setCamera(camera);
        subScene.setFocusTraversable(false);
        getChildren().add(subScene);

        setFocusTraversable(true);
        updateBackground();

        mousePressedHandler = e -> {
            lastX = e.getSceneX();
            lastY = e.getSceneY();
            requestFocus();
        };
        mouseDraggedHandler = e -> {
            double dx = e.getSceneX() - lastX;
            double dy = e.getSceneY() - lastY;
            lastX = e.getSceneX();
            lastY = e.getSceneY();
            if (e.isSecondaryButtonDown() || e.isMiddleButtonDown() || e.isShiftDown()) {
                pan(dx, dy);
            } else {
                orbit(dx, dy);
            }
            e.consume();
        };
        scrollHandler = e -> {
            double delta = e.getDeltaY();
            if (delta == 0 && e.getDeltaX() != 0) {
                delta = e.getDeltaX();
            }
            if (delta != 0) {
                zoom(Math.exp(-delta / 100));
            }
            e.consume();
        };
        keyHandler = e -> {
            switch (e.getCode()) {
                case LEFT -> orbit(-KEYBOARD_ROTATE_STEP, 0);
                case RIGHT -> orbit(KEYBOARD_ROTATE_STEP, 0);
                case UP -> orbit(0, KEYBOARD_ROTATE_STEP);
                case DOWN -> orbit(0, -KEYBOARD_ROTATE_STEP);
                case ADD, EQUALS -> zoom(ZOOM_STEP);
                case SUBTRACT, MINUS -> zoom(1 / ZOOM_STEP);
                case R -> resetCamera();
                default -> {
                    return;
                }
            }
            e.consume();
        };

        addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        addEventHandler(ScrollEvent.SCROLL, scrollHandler);
        addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);

        FXUtils.onWeakChange(Themes.colorSchemeProperty(), ignored -> updateBackground());
    }

    /// Returns the group into which mesh chunks should be added while the model is built.
    public Group getModelGroup() {
        return modelGroup;
    }

    /// Sets the world position of the model center, so that the camera can orbit around it.
    ///
    /// @param centerX the X coordinate of the model center in world (block) space
    /// @param centerY the Y coordinate of the model center in world (block) space
    /// @param centerZ the Z coordinate of the model center in world (block) space
    public void setModelCenter(double centerX, double centerY, double centerZ) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        updateModelTransforms();
    }

    /// Fits the camera to the given bounding box of the model.
    ///
    /// @param sizeX the size of the model along the X axis, in block units
    /// @param sizeY the size of the model along the Y axis, in block units
    /// @param sizeZ the size of the model along the Z axis, in block units
    public void fitCamera(double sizeX, double sizeY, double sizeZ) {
        double maxDim = Math.max(Math.max(sizeX, sizeY), sizeZ);
        fitDistance = Math.max(MIN_DISTANCE, maxDim / 2 / Math.tan(Math.toRadians(FIELD_OF_VIEW / 2)) * 1.35);
        camera.setFarClip(Math.max(1000, fitDistance * 8));
        camera.setNearClip(Math.min(1, Math.max(fitDistance / 1000, 0.01)));
        resetCamera();
    }

    /// Resets the camera to the fitted default view.
    public void resetCamera() {
        yaw.setAngle(0);
        pitch.setAngle(35);
        distance = fitDistance;
        pan(0, 0, true);
        updateCamera();
    }

    /// Removes event handlers and drops the model, releasing GPU and heap resources.
    public void dispose() {
        removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
        removeEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        modelGroup.getChildren().clear();
    }

    /// Keeps the sub-scene size in sync with the region size assigned by the parent layout.
    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width > 0 && height > 0) {
            subScene.setWidth(width);
            subScene.setHeight(height);
        }
    }

    /// Creates the lighting rig: a dim ambient light plus two directional point lights.
    private Group createLights() {
        Group lights = new Group();

        javafx.scene.AmbientLight ambient = new javafx.scene.AmbientLight(Color.gray(0.42));
        lights.getChildren().add(ambient);

        javafx.scene.PointLight topLight = new javafx.scene.PointLight(Color.gray(0.85));
        topLight.setTranslateX(200);
        topLight.setTranslateY(300);
        topLight.setTranslateZ(200);
        lights.getChildren().add(topLight);

        javafx.scene.PointLight bottomLight = new javafx.scene.PointLight(Color.gray(0.55));
        bottomLight.setTranslateX(-250);
        bottomLight.setTranslateY(-150);
        bottomLight.setTranslateZ(-250);
        lights.getChildren().add(bottomLight);

        return lights;
    }

    private void orbit(double dx, double dy) {
        yaw.setAngle(yaw.getAngle() + dx * ORBIT_SENSITIVITY);
        pitch.setAngle(Math.max(-89, Math.min(89, pitch.getAngle() - dy * ORBIT_SENSITIVITY)));
    }

    /// Pans the model so that it follows the mouse cursor.
    private void pan(double dx, double dy, boolean reset) {
        if (reset) {
            modelGroup.setTranslateX(-centerX);
            modelGroup.setTranslateY(-centerY);
            modelGroup.setTranslateZ(-centerZ);
        } else {
            double unitsPerPixel = 2 * distance * Math.tan(Math.toRadians(FIELD_OF_VIEW / 2)) / Math.max(subScene.getHeight(), 1);
            modelGroup.setTranslateX(modelGroup.getTranslateX() + dx * unitsPerPixel);
            modelGroup.setTranslateY(modelGroup.getTranslateY() + dy * unitsPerPixel);
        }
    }

    private void pan(double dx, double dy) {
        pan(dx, dy, false);
    }

    private void zoom(double factor) {
        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance * factor));
        updateCamera();
    }

    private void updateModelTransforms() {
        modelGroup.setTranslateX(-centerX);
        modelGroup.setTranslateY(-centerY);
        modelGroup.setTranslateZ(-centerZ);
    }

    private void updateCamera() {
        camera.setTranslateZ(-distance);
    }

    /// Applies the launcher color scheme surface color as the 3D background.
    private void updateBackground() {
        subScene.setFill(Themes.getColorScheme().getColor(ColorRole.SURFACE_CONTAINER));
    }
}
