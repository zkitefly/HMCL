/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.multiplayer;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.Locales;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MultiplayerPageSkin extends DecoratorAnimatedPage.DecoratorAnimatedPageSkin<MultiplayerPage> {

    private ObservableList<Node> clients;

    /**
     * Constructor for all SkinBase instances.
     *
     * @param control The control for which this Skin should attach to.
     */
    protected MultiplayerPageSkin(MultiplayerPage control) {
        super(control);

        // 保留左侧边栏
        {
            AdvancedListBox sideBar = new AdvancedListBox()
                    .addNavigationDrawerItem(i18n("version.launch"), SVG.SETTINGS, () -> {
                        control.launchGame();
                    })
                    .startCategory(i18n("help"))
                    .addNavigationDrawerItem(i18n("help"), SVG.SETTINGS, () -> {
                        FXUtils.openLink("https://docs.hmcl.net/multiplayer");
                    })
                    .addNavigationDrawerItem(i18n("multiplayer.help.1"), SVG.SETTINGS, () -> {
                        FXUtils.openLink("https://docs.hmcl.net/multiplayer/admin.html");
                    })
                    .addNavigationDrawerItem(i18n("multiplayer.help.2"), SVG.SETTINGS, () -> {
                        FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html");
                    })
                    .addNavigationDrawerItem(i18n("multiplayer.help.3"), SVG.SETTINGS, () -> {
                        FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html#%E5%88%9B%E5%BB%BA%E6%96%B9");
                    })
                    .addNavigationDrawerItem(i18n("multiplayer.help.4"), SVG.SETTINGS, () -> {
                        FXUtils.openLink("https://docs.hmcl.net/multiplayer/help.html#%E5%8F%82%E4%B8%8E%E8%80%85");
                    })
                    .addNavigationDrawerItem(i18n("multiplayer.help.text"), SVG.SETTINGS, () -> {
                        FXUtils.openLink("https://docs.hmcl.net/multiplayer/text.html");
                    })
                    .addNavigationDrawerItem(i18n("feedback"), SVG.SETTINGS, () -> {
                        // HMCLService.openRedirectLink("multiplayer-feedback")
                    });
            FXUtils.setLimitWidth(sideBar, 200);
            setLeft(sideBar);
        }

        {
            VBox content = new VBox(16);
            content.setPadding(new Insets(10));
            content.setFillWidth(true);
            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            setCenter(scrollPane);

            // 创建房间框
            ComponentList createRoomPane = new ComponentList();
            {
                createRoomPane.setTitle(i18n("multiplayer.create_room"));

                // 游戏局域网端口
                HBox portBox = new HBox(8);
                Label portLabel = new Label(i18n("multiplayer.port"));
                JFXTextField portField = new JFXTextField();
                portField.setPromptText("1024-65535");
                portField.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (!newValue.matches("\\d*")) {
                        portField.setText(newValue.replaceAll("[^\\d]", ""));
                    } else {
                        int value = Integer.parseInt(newValue.isEmpty() ? "0" : newValue);
                        if (value < 1024 || value > 65535) {
                            portField.getStyleClass().add("error");
                        } else {
                            portField.getStyleClass().remove("error");
                        }
                    }
                });
                portBox.getChildren().addAll(portLabel, portField);
                createRoomPane.getContent().add(portBox);

                // 禁用P2P开关
                HBox p2pBox = new HBox(8);
                Label p2pLabel = new Label(i18n("multiplayer.disable_p2p"));
                JFXToggleButton p2pToggle = new JFXToggleButton();
                p2pBox.getChildren().addAll(p2pLabel, p2pToggle);
                createRoomPane.getContent().add(p2pBox);

                // 自定义服务器开关和输入框
                HBox serverBox = new HBox(8);
                Label serverLabel = new Label(i18n("multiplayer.custom_server"));
                JFXToggleButton serverToggle = new JFXToggleButton();
                JFXTextField serverField = new JFXTextField();
                serverField.setPromptText("tcp://server:port");
                serverField.setVisible(false);
                serverToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    serverField.setVisible(newValue);
                });
                serverBox.getChildren().addAll(serverLabel, serverToggle, serverField);
                createRoomPane.getContent().add(serverBox);

                // 创建按钮
                JFXButton createButton = new JFXButton(i18n("multiplayer.create"));
                createButton.getStyleClass().add("jfx-button-raised");
                createButton.setOnAction(e -> {
                    String port = portField.getText();
                    if(port.isEmpty() || Integer.parseInt(port) < 1024 || Integer.parseInt(port) > 65535) {
                        Controllers.showToast(i18n("multiplayer.port.invalid"));
                        return;
                    }

                    control.startRoom(
                            Integer.parseInt(port),
                            p2pToggle.isSelected(),
                            serverToggle.isSelected() ? serverField.getText() : "tcp://public.easytier.cn:11010"
                    );
                });
                createRoomPane.getContent().add(createButton);
            }

            // 加入房间框
            ComponentList joinRoomPane = new ComponentList();
            {
                joinRoomPane.setTitle(i18n("multiplayer.join_room"));

                // 联机码输入框
                HBox codeBox = new HBox(8);
                Label codeLabel = new Label(i18n("multiplayer.join_code"));
                JFXTextField codeField = new JFXTextField();
                codeField.setPromptText(i18n("multiplayer.join_code.prompt"));
                codeBox.getChildren().addAll(codeLabel, codeField);
                joinRoomPane.getContent().add(codeBox);

                // 加入按钮
                JFXButton joinButton = new JFXButton(i18n("multiplayer.join"));
                joinButton.getStyleClass().add("jfx-button-raised");
                joinButton.setOnAction(e -> {
                    String code = codeField.getText();
                    if(code.isEmpty()) {
                        Controllers.showToast(i18n("multiplayer.join_code.empty"));
                        return;
                    }

                    try {
                        control.joinRoom(code);
                    } catch(Exception ex) {
                        Controllers.showToast(i18n("multiplayer.join_code.invalid"));
                    }
                });
                joinRoomPane.getContent().add(joinButton);
            }

            content.getChildren().addAll(
                    createRoomPane,
                    joinRoomPane
            );
        }
    }

}