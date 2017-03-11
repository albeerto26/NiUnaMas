package quercus.seg.voicehelp;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SpeechLoggerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "SpeechLoggerActivity";
    private static final int PERMISSIONS_AUDIO_AND_CALL = 1;
    public static final String EXTRA_LOCATION = "LOCATION";

    Map<String, Integer> keywordsFrequencies = new HashMap<>();
    Map<String, Integer> keywordsViewsIds = new HashMap<>();
    public static final int KEYWORD_SIZE = 32;
    public static final int KEYWORD_SIZE_MULTIPLIER = 20;

    boolean wasCalledAlready = false;

    // ****** SOLO PARA PRUEBAS ******
    Random random = new Random();
    final String[] keywords = new String[]{"Manzana", "Pera", "Naranja", "Cereza"};
    // *******************************

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.table_keywords)
    TableLayout keywordsTable;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_speech_logger);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        ButterKnife.bind(this);
        EventBus.getDefault().register(this);

        setSupportActionBar(toolbar);
        checkRecordAudioPermission(this);
        startService(new Intent(this, VoiceService.class));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // Add a marker in Sydney and move the camera
        LatLng yourPosition = new LatLng(39.476837, -6.335944);
        googleMap.addMarker(new MarkerOptions().position(yourPosition).title("Tu posición"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(yourPosition));
        googleMap.animateCamera(CameraUpdateFactory.zoomTo(15));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // testLogKeyword();
    }

    private void checkRecordAudioPermission(final Activity thisActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(thisActivity, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(thisActivity, Manifest.permission.CALL_PHONE)
                            != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Se necesitan permisos para grabar audio y realizar llamadas");
                builder.setMessage("Por favor, concede permisos a esta aplicación para que pueda" +
                        " grabar el audio del micrófono y llamar a la policía ante una situación de emergencia");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        ActivityCompat.requestPermissions(thisActivity,
                                new String[]{Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.CALL_PHONE},
                                PERMISSIONS_AUDIO_AND_CALL);
                    }
                });
                builder.show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_AUDIO_AND_CALL: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "RECORD_AUDIO permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Sin funcionalidad");
                    builder.setMessage("Esta aplicación necesita varios permisos para funcionar");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onKeywordDetectedEvent(KeywordDetectedEvent event) {
        logKeyword(event.getDetectedKeyword());
    }

    /**
     * Realiza una llamada de emergencia al número
     * configurado en las preferencias (por defecto 091).
     */
    private void emergencyCall() {
        if (!wasCalledAlready) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            String emergencyPhone = sp.getString("emergencyPhone", "648609079");
            Log.d(TAG, "Llamando a " + emergencyPhone);
            Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + emergencyPhone));
            callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);
            wasCalledAlready = true;
        } else {
            Log.d(TAG, "Ya se ha realizado una llamada de emergencia");
        }
    }

    /**
     * Muestra en la pantalla la palabra clave detectada. Si se repite varias veces, se destaca
     * con un tamaño de fuente más grande.
     *
     * @param keyword la palabra detectada
     */
    public void logKeyword(String keyword) {

        Log.d(TAG, "Logging keyword " + keyword);

        int keywordFrequency;
        int keywordViewId;

        if (keywordsViewsIds.containsKey(keyword)) {

            keywordFrequency = keywordsFrequencies.get(keyword) + 1;
            keywordsFrequencies.put(keyword, keywordFrequency);
            keywordViewId = keywordsViewsIds.get(keyword);

        } else {

            keywordFrequency = 0;
            keywordsFrequencies.put(keyword, keywordFrequency);
            keywordViewId = keywordsViewsIds.size() + 100;
            keywordsViewsIds.put(keyword, keywordViewId);

            TextView keywordView = new TextView(this.getApplicationContext());
            keywordView.setId(keywordViewId);
            keywordView.setText(keyword);
            keywordView.setTextColor(Color.BLACK);
            keywordView.setGravity(Gravity.CENTER);

            TableRow row = new TableRow(this.getApplicationContext());
            row.setLayoutParams(new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT));
            row.setGravity(Gravity.CENTER_HORIZONTAL);
            row.addView(keywordView);
            keywordsTable.addView(row);
        }

        TextView keywordView = (TextView) findViewById(keywordViewId);
        // Insertamos cada palabra ocupando una fila en la pantalla

        float textSize = KEYWORD_SIZE + (keywordFrequency * KEYWORD_SIZE_MULTIPLIER);
        keywordView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        if (keywordFrequency > 1) {
            emergencyCall();
            keywordView.setTextColor(Color.RED);
        }

    }

    /**
     * Usarlo solo para probar que las palabras se añaden correctamente.
     */
    private void testLogKeyword() {
        int numIterations = 15;
        Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 1; i <= numIterations; i++) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    logKeyword(keywords[random.nextInt(keywords.length)]);
                }
            }, 1000 * i);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, MyPreferenceActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}

