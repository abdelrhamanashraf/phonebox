package local.phonebox.geckotest;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/** Experimental GeckoView panel; pairing credentials stay in app-private storage. */
public class MainActivity extends Activity {
    private GeckoSession session;
    private PanelServer server;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try { server = new PanelServer(this); } catch (Exception e) { android.widget.TextView error = new android.widget.TextView(this); error.setText("Could not start panel: " + e.getMessage()); setContentView(error); return; }
        GeckoView view = new GeckoView(this);
        setContentView(view);
        GeckoRuntime runtime = GeckoRuntime.getDefault(this);
        session = new GeckoSession();
        session.open(runtime);
        view.setSession(session);
        session.loadUri(server.url());
    }
    @Override protected void onDestroy() { if (session != null) session.close(); if (server != null) server.close(); super.onDestroy(); }
}
