package quercus.seg.voicehelp;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

/**
 * Created by Fernando Diaz on 9/03/17.
 */

public class VoiceService extends Service implements RecognitionListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "VoiceService";

    private static final String ALERT_SEARCH = "FRASE_DE_ALERTA";
    private static final String KEYWORDS_SEARCH = "PALABRAS_CLAVE";

    private static final String PREF_KEY_KEYPHRASE = "keyphrase";
    private String keyphrase;
    private static final String PREF_KEY_EMERGENCY_PHONE = "emergencyPhone";
    private String emergencyPhone;

    private SpeechRecognizer recognizer;

    String stringLocation;

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            stringLocation = location.getLatitude() + ", " + location.getLongitude();
            Log.d(TAG, stringLocation);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Iniciando el servicio. Configurando el reconocedor de voz");
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000*60*15, 0, mLocationListener);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        sp.registerOnSharedPreferenceChangeListener(this);
        this.keyphrase = sp.getString("keyphrase", "necesito ayuda");
        this.emergencyPhone = sp.getString("emergencyPhone", "600000000");
        runRecognizerSetup();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        Log.d(TAG, "Las preferencias han cambiado");
        recognizer.stop();
        recognizer.shutdown();
        switch (key) {
            case PREF_KEY_KEYPHRASE:
                this.keyphrase = sp.getString("keyphrase", "necesito ayuda");
                recognizer.addKeyphraseSearch(ALERT_SEARCH, keyphrase);
                break;
            case PREF_KEY_EMERGENCY_PHONE:
                this.emergencyPhone = sp.getString("emergencyPhone", "600000000");
                break;
            default:
                break;
        }
        runRecognizerSetup();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    private void runRecognizerSetup() {
        /*
        La inicialización del reconocedor de voz consume tiempo e implica I/O,
        por lo que lo ejecutamos en una AsyncTask.
         */
        new AsyncTask<Void, Void, Exception>() {

            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(VoiceService.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception error) {
                if (error != null) {
                    Log.e(TAG, "Error al iniciar el reconocedor de voz", error);
                } else {
                    Log.d(TAG, "Reconocedor configurado correctamente");
                    Log.d(TAG, "Intentando reconocer " + keyphrase);
                    switchSearch(ALERT_SEARCH);
                }
            }

        }.execute();
    }

    private void setupRecognizer(File assetsDir) throws IOException {

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "es-es-ptm"))
                .setDictionary(new File(assetsDir, "es.dict"))
                .setRawLogDir(assetsDir) // Para desactivar el log de audio raw, comentar esta llamada
                .getRecognizer();
        recognizer.addListener(this);

        /*
        Añadimos una búsqueda por frase clave.
        Cuando se reconoce la frase clave se llama al método onPartialResult.
         */
        recognizer.addKeyphraseSearch(ALERT_SEARCH, keyphrase);

        File keywordsFile = new File(assetsDir, "keywords.list");
        recognizer.addKeywordSearch(KEYWORDS_SEARCH, keywordsFile);
    }

    private void switchSearch(String searchName) {
        Log.d(TAG, "Cambiando a búsqueda: " + searchName);
        recognizer.stop();
        switch (searchName) {
            case KEYWORDS_SEARCH:
                recognizer.startListening(KEYWORDS_SEARCH);
                break;
            case ALERT_SEARCH:
                recognizer.startListening(ALERT_SEARCH);
                break;
            default:
                recognizer.startListening(ALERT_SEARCH);
                break;
        }
    }

    /**
     * Aquí obtenemos actualizaciones rápidas sobre la hipótesis actual. Cuando se intenta
     * detectar una frase o una palabra clave podemos responder aquí. En otros casos habría
     * que esperar al resultado final en {@link #onResult(Hypothesis)}
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            if (text.equals(keyphrase)) {
                Log.d(TAG, "Frase clave reconocida: " + text);
                switchSearch(KEYWORDS_SEARCH);
                Intent intent = new Intent(this, SpeechLoggerActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(SpeechLoggerActivity.EXTRA_LOCATION, stringLocation);
                startActivity(intent);
            } else {
                String lastKeyword = text.split(" ")[0];
                recognizer.cancel();
                recognizer.startListening(KEYWORDS_SEARCH);
                Log.d(TAG, "Palabra clave reconocida: " + lastKeyword);
                EventBus.getDefault().post(new KeywordDetectedEvent(lastKeyword));
            }
        }
    }

    /**
     * Este callback se llama cuando paramos el reconocedor.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            Log.d(TAG, "onResult. Texto reconocido: " + text);
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech");
    }

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech");
    }

    @Override
    public void onError(Exception error) {
        Log.e(TAG, "Error", error);
    }

    @Override
    public void onTimeout() {
        Log.d(TAG, "onTimeout");
        switchSearch(ALERT_SEARCH);
    }


}
