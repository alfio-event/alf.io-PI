package io.alf;

import io.alf.manager.CheckinManager;
import io.alf.scene.InitializeWithQRCode;
import io.alf.scene.Scan;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class Router {

    private final Map<String, Supplier<SceneBuilder>> routes = new HashMap<>();
    private final Stage stage;

    Router(Stage stage, CheckinManager checkinManager) {
        this.stage = stage;
        routes.put("/", () -> new InitializeWithQRCode(checkinManager, this));
        routes.put("/scan", () -> new Scan(checkinManager));
    }


    public void navigate(String route) {
        stage.setScene(routes.get(route).get().build());
        stage.show();
    }


    public interface SceneBuilder {
        Scene build();
    }
}
