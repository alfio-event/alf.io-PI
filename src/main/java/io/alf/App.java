package io.alf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.alf.manager.CheckinManager;
import io.alf.manager.ExternalResourceManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.concurrent.*;

/**
 * Hello world!
 *
 */
public class App extends Application {

    private final ExternalResourceManager externalResourceManager = new ExternalResourceManager();
    private final CheckinManager checkinManager = new CheckinManager(externalResourceManager);


    public static final ExecutorService executor = Executors.newSingleThreadExecutor();
    public static final Gson JSON = new GsonBuilder().create();

    @Override
    public void start(Stage stage) {
        Router r = new Router(stage, checkinManager);
        r.navigate("/");
        stage.setOnCloseRequest(e->{
            System.err.println("stopping");
            executor.shutdown();
            checkinManager.stop();
            Platform.exit();
            System.err.println("stopped");
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
