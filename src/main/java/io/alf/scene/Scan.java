package io.alf.scene;

import com.jfoenix.controls.JFXButton;
import io.alf.App;
import io.alf.Router;
import io.alf.manager.CheckinManager;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

public class Scan implements Router.SceneBuilder {

    private final CheckinManager checkinManager;

    public Scan(CheckinManager checkinManager) {
        this.checkinManager = checkinManager;
    }

    @Override
    public Scene build() {
        var l = new Label("Scan page here");
        var inputText = new TextArea();
        var button = new JFXButton("scan");
        button.setOnAction((e) -> {
            var scanned = inputText.getText();
            var t = new Task<Void>() {
                @Override
                protected Void call() {
                    checkinManager.checkIn(scanned);
                    return null;
                }
            };

            App.executor.execute(t);
        });
        Scene scene = new Scene(new VBox(l, inputText, button), 640, 480);
        return scene;
    }
}
