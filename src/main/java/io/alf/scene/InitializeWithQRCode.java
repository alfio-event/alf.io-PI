package io.alf.scene;

import com.jfoenix.controls.JFXButton;
import io.alf.App;
import io.alf.Router;
import io.alf.manager.CheckinManager;
import io.alf.model.QRCodeConfiguration;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

public class InitializeWithQRCode implements Router.SceneBuilder {

    private final CheckinManager checkinManager;
    private final Router router;

    private StringBuilder sb = new StringBuilder();

    public InitializeWithQRCode(CheckinManager checkinManager, Router router) {
        this.checkinManager = checkinManager;
        this.router = router;
    }


    public void loadEvent(QRCodeConfiguration conf) {
        var t = new Task<Void>() {
            @Override
            protected Void call() {
                try {
                    checkinManager.load(conf);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw t;
                }
                return null;
            }

            @Override
            protected void succeeded() {
                router.navigate("/scan");
            }
        };
        App.executor.execute(t);
    }

    @Override
    public Scene build() {
        var l = new Label("Scan QR code for initializing");
        var button = new JFXButton("Load conf");
        var conf = new QRCodeConfiguration("URL", "KEY", "EVENT", null, null);
        button.setOnAction((e) -> {
            loadEvent();
        });

        Scene scene = new Scene(new HBox(l, button), 640, 480);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, (key) -> {
            sb.append(key.getText());
            System.err.println(key);
            if (key.getCode() == KeyCode.ENTER) {
                sb.append("\n");
                System.err.println("enter pressed");
                System.err.println("full content is " + sb.toString());
                sb.setLength(0);
            }
        });
        return scene;
    }
}
