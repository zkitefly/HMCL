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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.util.StringConverter;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jackhuang.hmcl.schematic.LitematicBlockData;
import org.jackhuang.hmcl.schematic.resource.MinecraftResourcePack;
import org.jackhuang.hmcl.setting.StyleSheets;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.schematic.SchematicMeshBuilder.SchematicTooLargeException;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// A standalone window that previews a Litematic schematic in 3D.
///
/// The window first shows a loading state while the file is parsed on a background thread, then
/// renders the schematic progressively with a [SchematicView]. If parsing or mesh generation
/// fails, an error message with a retry option is shown instead.
@NotNullByDefault
public final class SchematicPreviewWindow extends Stage {

    private static final int DEFAULT_WIDTH = 900;
    private static final int DEFAULT_HEIGHT = 640;

    /// The rendering quality presets, balancing visual fidelity against memory usage.
    private enum Quality {
        HIGH("schematics.preview.quality.high", 200_000, 8_000_000),
        STANDARD("schematics.preview.quality.standard", 200_000, 4_000_000),
        LOW("schematics.preview.quality.low", 100_000, 2_000_000);

        private final String key;
        private final int chunkVertices;
        private final int maxVertices;

        Quality(String key, int chunkVertices, int maxVertices) {
            this.key = key;
            this.chunkVertices = chunkVertices;
            this.maxVertices = maxVertices;
        }

        /// Returns the localized label of this quality preset.
        String getLabel() {
            return i18n(key);
        }
    }

    private final Path file;

    private final @Nullable Path versionJar;

    private final SchematicView view = new SchematicView();
    private final StackPane viewPane = new StackPane();
    private final StackPane loadingPane = new StackPane();
    private final StackPane errorPane = new StackPane();
    private final VBox loadingBox = new VBox(16);
    private final JFXSpinner spinner = new JFXSpinner();
    private final Label loadingLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar();
    private final Label errorLabel = new Label();
    private final ComboBox<Quality> qualityBox = new ComboBox<>();

    /// The resource pack currently used for textured rendering, kept alive for the whole preview.
    private @Nullable MinecraftResourcePack resourcePack;

    private int loadGeneration;
    private boolean disposed;

    /// Creates the preview window for the given Litematic file without real textures.
    ///
    /// @param file the `.litematic` file to preview
    public SchematicPreviewWindow(Path file) {
        this(file, null);
    }

    /// Creates the preview window for the given Litematic file.
    ///
    /// When `versionJar` points to the client jar of the Minecraft version the schematic was
    /// built with, blocks are rendered with their real textures loaded from that jar. If the jar
    /// is missing or cannot be read, the preview silently falls back to solid colors.
    ///
    /// @param file       the `.litematic` file to preview
    /// @param versionJar the client jar of the game version, or null to render solid colors
    public SchematicPreviewWindow(Path file, @Nullable Path versionJar) {
        Themes.applyNativeDarkMode(this);

        this.file = Objects.requireNonNull(file, "file");
        this.versionJar = versionJar;

        setScene(new Scene(createContent(), DEFAULT_WIDTH, DEFAULT_HEIGHT));
        StyleSheets.init(getScene());
        setTitle(i18n("schematics.preview.title"));
        FXUtils.setIcon(this);
        onEscPressed(getScene().getRoot(), this::close);

        startLoad();
    }

    /// Disposes the 3D view when the window closes so that GPU resources are released.
    @Override
    public void close() {
        disposed = true;
        view.dispose();
        resourcePack = null;
        super.close();
    }

    private BorderPane createContent() {
        BorderPane root = new BorderPane();

        root.setTop(createToolbar());
        root.setCenter(createCenter());
        root.setBottom(createHintBar());

        return root;
    }

    private Node createToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));

        Label titleLabel = new Label(StringUtils.removeSuffix(file.getFileName().toString(), ".litematic"));
        titleLabel.setMaxWidth(420);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label qualityLabel = new Label(i18n("schematics.preview.quality"));

        qualityBox.getItems().setAll(Quality.values());
        qualityBox.setValue(Quality.STANDARD);
        qualityBox.setConverter(new StringConverter<Quality>() {
            @Override
            public String toString(Quality quality) {
                return quality == null ? null : quality.getLabel();
            }

            @Override
            public Quality fromString(String string) {
                return null;
            }
        });
        qualityBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                startLoad();
            }
        });

        JFXButton closeButton = new JFXButton(i18n("button.close"));
        closeButton.setOnAction(e -> close());

        toolbar.getChildren().addAll(titleLabel, spacer, qualityLabel, qualityBox, closeButton);
        return toolbar;
    }

    private Node createCenter() {
        viewPane.getChildren().add(view);

        loadingBox.setAlignment(Pos.CENTER);
        spinner.setPrefSize(64, 64);
        progressBar.setPrefWidth(320);
        progressBar.setVisible(false);
        loadingBox.getChildren().addAll(spinner, loadingLabel, progressBar);
        loadingPane.getChildren().add(loadingBox);
        loadingPane.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.5), CornerRadii.EMPTY, Insets.EMPTY)));

        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(520);
        errorLabel.setAlignment(Pos.CENTER);
        JFXButton retryButton = new JFXButton(i18n("schematics.preview.retry"));
        retryButton.getStyleClass().add("dialog-accept");
        retryButton.setOnAction(e -> startLoad());
        JFXButton errorCloseButton = new JFXButton(i18n("button.close"));
        errorCloseButton.setOnAction(e -> close());
        HBox errorButtons = new HBox(12, retryButton, errorCloseButton);
        errorButtons.setAlignment(Pos.CENTER);
        VBox errorBox = new VBox(16, errorLabel, errorButtons);
        errorBox.setAlignment(Pos.CENTER);
        errorPane.getChildren().add(errorBox);
        errorPane.setVisible(false);

        StackPane centerPane = new StackPane(viewPane, loadingPane, errorPane);
        return centerPane;
    }

    private Node createHintBar() {
        Label hint = new Label(i18n("schematics.preview.controls"));
        hint.setWrapText(true);
        hint.setPadding(new Insets(8, 16, 8, 16));
        hint.setAlignment(Pos.CENTER);
        return hint;
    }

    /// Starts the load pipeline: parse the file, then generate the mesh and render it.
    private void startLoad() {
        if (disposed) {
            return;
        }

        int generation = ++loadGeneration;
        Quality quality = qualityBox.getValue() == null ? Quality.STANDARD : qualityBox.getValue();
        qualityBox.setDisable(true);

        // Reset the UI to the parsing state.
        view.getModelGroup().getChildren().clear();
        resourcePack = null;
        viewPane.setVisible(false);
        errorPane.setVisible(false);
        showLoadingPhase(false);

        Task.supplyAsync(() -> LitematicBlockData.load(file))
                .whenComplete(Schedulers.javafx(), (data, exception) -> {
                    if (disposed || generation != loadGeneration) {
                        return;
                    }
                    if (exception != null) {
                        showError(exception);
                    } else {
                        startResourceLoad(data, quality, generation);
                    }
                })
                .start();
    }

    /// Loads the texture resource pack of the current game version on a background thread.
    ///
    /// A failure here is not fatal: the mesh builder falls back to solid colors.
    private void startResourceLoad(LitematicBlockData data, Quality quality, int generation) {
        if (versionJar == null) {
            startMeshBuild(data, null, quality, generation);
            return;
        }
        loadingLabel.setText(i18n("schematics.preview.textures"));
        Task.supplyAsync(() -> {
            try {
                return MinecraftResourcePack.load(versionJar, data.getPalette());
            } catch (Exception e) {
                LOG.warning("Failed to load block textures from " + versionJar + ", falling back to solid colors", e);
                return null;
            }
        }).whenComplete(Schedulers.javafx(), (pack, exception) -> {
            if (disposed || generation != loadGeneration) {
                return;
            }
            startMeshBuild(data, exception == null ? pack : null, quality, generation);
        }).start();
    }

    /// Builds the mesh on a background thread and streams the chunks into the view.
    private void startMeshBuild(LitematicBlockData data, @Nullable MinecraftResourcePack pack, Quality quality, int generation) {
        showLoadingPhase(true);
        resourcePack = pack;
        if (pack != null) {
            // JavaFX images must be created on the JavaFX Application Thread.
            pack.uploadAtlas();
        }

        view.fitCamera(data.getSizeX(), data.getSizeY(), data.getSizeZ());
        view.setModelCenter(
                (data.getMinX() + data.getMaxX()) / 2.0,
                (data.getMinY() + data.getMaxY()) / 2.0,
                (data.getMinZ() + data.getMaxZ()) / 2.0);
        viewPane.setVisible(true);

        Task.supplyAsync(() -> {
            SchematicMeshBuilder builder = new SchematicMeshBuilder(quality.chunkVertices, quality.maxVertices, pack);
            builder.build(data, progress -> {
                if (disposed || generation != loadGeneration) {
                    return;
                }
                Platform.runLater(() -> progressBar.setProgress(progress));
            }, chunk -> {
                if (disposed || generation != loadGeneration) {
                    return;
                }
                Platform.runLater(() -> view.getModelGroup().getChildren().add(chunk));
            });
            return null;
        }).whenComplete(Schedulers.javafx(), (ignored, exception) -> {
            if (disposed || generation != loadGeneration) {
                return;
            }
            if (exception == null) {
                loadingPane.setVisible(false);
                qualityBox.setDisable(false);
                view.requestFocus();
            } else {
                showError(exception);
            }
        }).start();
    }

    /// Switches the loading overlay between the parsing spinner and the build progress bar.
    private void showLoadingPhase(boolean building) {
        spinner.setVisible(!building);
        progressBar.setVisible(building);
        progressBar.setProgress(building ? 0 : ProgressBar.INDETERMINATE_PROGRESS);
        loadingLabel.setText(building ? i18n("schematics.preview.building") : i18n("schematics.preview.loading"));
        loadingPane.setVisible(true);
        errorPane.setVisible(false);
    }

    /// Shows a user-friendly error message with a retry option.
    private void showError(Throwable exception) {
        LOG.warning("Failed to preview schematic " + file, exception);

        String message;
        if (exception instanceof SchematicTooLargeException tooLarge) {
            message = i18n("schematics.preview.error.too_large", tooLarge.getMaxVertices());
        } else if (exception instanceof IOException) {
            message = i18n("schematics.preview.error.parse", file.getFileName());
        } else {
            message = i18n("schematics.preview.error.unknown", exception);
        }

        errorLabel.setText(message);
        loadingPane.setVisible(false);
        errorPane.setVisible(true);
        qualityBox.setDisable(false);
    }
}
