package tech.titikkoma.cerebro;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.neurosky.AlgoSdk.NskAlgoDataType;
import com.neurosky.AlgoSdk.NskAlgoSdk;
import com.neurosky.AlgoSdk.NskAlgoSignalQuality;
import com.neurosky.AlgoSdk.NskAlgoState;
import com.neurosky.AlgoSdk.NskAlgoType;
import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.DataType.MindDataType;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ProfileActivity extends AppCompatActivity {

    private final String TAG = "tkd";

    // COMM SDK handles
    private TgStreamReader tgStreamReader;
    private BluetoothAdapter mBluetoothAdapter;

    // internal variables
    private boolean bInited = false;
    private boolean bRunning = false;
    private NskAlgoType currentSelectedAlgo;

    // canned data variables
    private short raw_data[] = {0};
    private int raw_data_index= 0;
    private float output_data[];
    private int output_data_count = 0;
    private int raw_data_sec_len = 85;

    private View dataView;

    private TextView attentionValue;
    private TextView calmnessValue;

    private NskAlgoSdk nskAlgoSdk;

    private ProgressDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        dataView = findViewById(R.id.data);
        loadingDialog = new ProgressDialog(ProfileActivity.this);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                headsetSetup();
                sendData();
            }
        });

        attentionValue = (TextView) findViewById(R.id.attention);
        calmnessValue = (TextView) findViewById(R.id.calmness);

        nskAlgoSdk = new NskAlgoSdk();
        algoSetup();

        try {
            // (1) Make sure that the device supports Bluetooth and Bluetooth is on
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Toast.makeText(
                        this,
                        "Please enable your Bluetooth and re-run this program!",
                        Toast.LENGTH_LONG).show();
                //finish();
            } else if (mBluetoothAdapter.isEnabled()) {
                headsetSetup();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "error:" + e.getMessage());
            return;
        }

        nskAlgoSdk.setOnStateChangeListener(new NskAlgoSdk.OnStateChangeListener() {
            @Override
            public void onStateChange(int state, int reason) {
                String stateStr = "";
                String reasonStr = "";
                for (NskAlgoState s : NskAlgoState.values()) {
                    if (s.value == state) {
                        stateStr = s.toString();
                    }
                }
                for (NskAlgoState r : NskAlgoState.values()) {
                    if (r.value == reason) {
                        reasonStr = r.toString();
                    }
                }
                Log.d(TAG, "NskAlgoSdkStateChangeListener: state: " + stateStr + ", reason: " + reasonStr);
                final String finalStateStr = stateStr + " | " + reasonStr;
                final int finalState = state;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
//                        stateText.setText(finalStateStr);

                        if (finalState == NskAlgoState.NSK_ALGO_STATE_RUNNING.value || finalState == NskAlgoState.NSK_ALGO_STATE_COLLECTING_BASELINE_DATA.value) {
                            bRunning = true;
//                            startButton.setText("Pause");
//                            startButton.setEnabled(true);
//                            stopButton.setEnabled(true);
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_STOP.value) {
                            bRunning = false;
                            raw_data = null;
                            raw_data_index = 0;
//                            startButton.setText("Start");
//                            startButton.setEnabled(true);
//                            stopButton.setEnabled(false);
//
//                            headsetButton.setEnabled(true);
//                            cannedButton.setEnabled(true);

                            if (tgStreamReader != null && tgStreamReader.isBTConnected()) {

                                // Prepare for connecting
                                tgStreamReader.stop();
                                tgStreamReader.close();
                            }

                            output_data_count = 0;
                            output_data = null;

                            System.gc();
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_PAUSE.value) {
                            bRunning = false;
//                            startButton.setText("Start");
//                            startButton.setEnabled(true);
//                            stopButton.setEnabled(true);
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_ANALYSING_BULK_DATA.value) {
                            bRunning = true;
//                            startButton.setText("Start");
//                            startButton.setEnabled(false);
//                            stopButton.setEnabled(true);
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_INITED.value || finalState == NskAlgoState.NSK_ALGO_STATE_UNINTIED.value) {
                            bRunning = false;
//                            startButton.setText("Start");
//                            startButton.setEnabled(true);
//                            stopButton.setEnabled(false);
                        }
                    }
                });
            }
        });

        nskAlgoSdk.setOnSignalQualityListener(new NskAlgoSdk.OnSignalQualityListener() {
            @Override
            public void onSignalQuality(final int level) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        String sqStr = NskAlgoSignalQuality.values()[level].toString();
//                        sqText.setText(sqStr);
                    }
                });
            }
        });

        nskAlgoSdk.setOnAttAlgoIndexListener(new NskAlgoSdk.OnAttAlgoIndexListener() {
            @Override
            public void onAttAlgoIndex(int value) {
                Log.d(TAG, "NskAlgoAttAlgoIndexListener: Attention:" + value);
                String msg = "";
                if (value > 60) {
                    msg = " - Tinggi";
                } else if (value > 40 && value <= 60) {
                    msg = " - Normal";
                } else {
                    msg = " - Rendah";
                }
                String attStr = value + "%" + msg;
                final String finalAttStr = attStr;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        attentionValue.setText(finalAttStr);
                    }
                });
            }
        });

        nskAlgoSdk.setOnMedAlgoIndexListener(new NskAlgoSdk.OnMedAlgoIndexListener() {
            @Override
            public void onMedAlgoIndex(int value) {
                Log.d(TAG, "NskAlgoMedAlgoIndexListener: Meditation:" + value);
                String msg = "";
                if (value > 60) {
                    msg = " - Santai";
                } else if (value > 40 && value <= 60) {
                    msg = " - Normal";
                } else {
                    msg = " - Tertekan";
                }
                String medStr = value + "%" + msg;
                final String finalMedStr = medStr;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        calmnessValue.setText(finalMedStr);
                    }
                });
            }
        });
    }

    public void headsetSetup() {
        output_data_count = 0;
        output_data = null;

        raw_data = new short[512];
        raw_data_index = 0;

        // Example of constructor public TgStreamReader(BluetoothAdapter ba, TgStreamHandler tgStreamHandler)
        tgStreamReader = new TgStreamReader(mBluetoothAdapter,callback);

        if(tgStreamReader != null && tgStreamReader.isBTConnected()){

            // Prepare for connecting
            tgStreamReader.stop();
            tgStreamReader.close();
        }

        // (4) Demo of  using connect() and start() to replace connectAndStart(),
        // please call start() when the state is changed to STATE_CONNECTED
        tgStreamReader.connect();
    }

    public void algoSetup() {
        // check selected algos
        int algoTypes = 0;// = NskAlgoType.NSK_ALGO_TYPE_CR.value;

        currentSelectedAlgo = NskAlgoType.NSK_ALGO_TYPE_INVALID;

        algoTypes += NskAlgoType.NSK_ALGO_TYPE_MED.value;
        algoTypes += NskAlgoType.NSK_ALGO_TYPE_ATT.value;
        algoTypes += NskAlgoType.NSK_ALGO_TYPE_BLINK.value;
        // algoTypes += NskAlgoType.NSK_ALGO_TYPE_BP.value;

        if (bInited) {
            nskAlgoSdk.NskAlgoUninit();
            bInited = false;
        }
        int ret = nskAlgoSdk.NskAlgoInit(algoTypes, getFilesDir().getAbsolutePath());
        if (ret == 0) {
            bInited = true;
        }

        Log.d(TAG, "NSK_ALGO_Init() " + ret);
        String sdkVersion = "SDK ver.: " + nskAlgoSdk.NskAlgoSdkVersion();

        if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_ATT.value) != 0) {
            sdkVersion += "\nATT ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_ATT.value);
        }
        if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_MED.value) != 0) {
            sdkVersion += "\nMED ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_MED.value);
        }
        if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_BLINK.value) != 0) {
            sdkVersion += "\nBlink ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_BLINK.value);
        }
        if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_BP.value) != 0) {
            sdkVersion += "\nEEG Bandpower ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_BP.value);
        }
//        showToast(sdkVersion, Toast.LENGTH_LONG);
    }

    private short [] readData(InputStream is, int size) {
        short data[] = new short[size];
        int lineCount = 0;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            while (lineCount < size) {
                String line = reader.readLine();
                if (line == null || line.isEmpty()) {
                    Log.d(TAG, "lineCount=" + lineCount);
                    break;
                }
                data[lineCount] = Short.parseShort(line);
                lineCount++;
            }
            Log.d(TAG, "lineCount=" + lineCount);
        } catch (IOException e) {

        }
        return data;
    }

    @Override
    public void onBackPressed() {
        nskAlgoSdk.NskAlgoUninit();
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_profile, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private TgStreamHandler callback = new TgStreamHandler() {

        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            Log.d(TAG, "connectionStates change to: " + connectionStates);
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTING:
                    // Do something when connecting
                    loadingDialog.setIndeterminate(false);
                    loadingDialog.setCancelable(false);
                    loadingDialog.setCanceledOnTouchOutside(false);
                    loadingDialog.setTitle("Please wait");
                    loadingDialog.setMessage("Connecting to device...");
                    loadingDialog.show();
                    break;
                case ConnectionStates.STATE_CONNECTED:
                    // Do something when connected
                    tgStreamReader.start();
                    loadingDialog.dismiss();
                    showSnackbar("Connected");
                    nskAlgoSdk.NskAlgoStart(false);
                    break;
                case ConnectionStates.STATE_WORKING:
                    // Do something when working

                    //(9) demo of recording raw data , stop() will call stopRecordRawData,
                    //or you can add a button to control it.
                    //You can change the save path by calling setRecordStreamFilePath(String filePath) before startRecordRawData
                    //tgStreamReader.startRecordRawData();

                    ProfileActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
//                            Button startButton = (Button) findViewById(R.id.startButton);
//                            startButton.setEnabled(true);
                        }

                    });

                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    // Do something when getting data timeout

                    //(9) demo of recording raw data, exception handling
                    //tgStreamReader.stopRecordRawData();

//                    showToast("Get data time out!", Toast.LENGTH_SHORT);

                    if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                        tgStreamReader.stop();
                        tgStreamReader.close();
                    }

                    break;
                case ConnectionStates.STATE_STOPPED:
                    // Do something when stopped
                    // We have to call tgStreamReader.stop() and tgStreamReader.close() much more than
                    // tgStreamReader.connectAndstart(), because we have to prepare for that.
                    Log.d(TAG, "Stopped");
                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    // Do something when disconnected
                    loadingDialog.dismiss();
                    showSnackbar("Disconnected");
                    break;
                case ConnectionStates.STATE_ERROR:
                    // Do something when you get error message
                    Log.d(TAG, "Error");
                    loadingDialog.dismiss();
                    break;
                case ConnectionStates.STATE_FAILED:
                    // Do something when you get failed message
                    // It always happens when open the BluetoothSocket error or timeout
                    // Maybe the device is not working normal.
                    // Maybe you have to try again
                    loadingDialog.dismiss();
                    showSnackbar("Failed to connect");
                    break;
            }
        }

        @Override
        public void onRecordFail(int flag) {
            // You can handle the record error message here
            Log.e(TAG,"onRecordFail: " +flag);

        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // You can handle the bad packets here.
        }

        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // You can handle the received data here
            // You can feed the raw data to algo sdk here if necessary.
            //Log.i(TAG,"onDataReceived");
            switch (datatype) {
                case MindDataType.CODE_ATTENTION:
                    short attValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_ATT.value, attValue, 1);
                    break;
                case MindDataType.CODE_MEDITATION:
                    short medValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_MED.value, medValue, 1);
                    break;
                case MindDataType.CODE_POOR_SIGNAL:
                    short pqValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_PQ.value, pqValue, 1);
                    break;
                case MindDataType.CODE_RAW:
                    raw_data[raw_data_index++] = (short)data;
                    if (raw_data_index == 512) {
                        nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value, raw_data, raw_data_index);
                        raw_data_index = 0;
                    }
                    break;
                default:
                    break;
            }
        }

    };

    public void showToast(final String msg, final int timeStyle) {
        ProfileActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }

        });
    }

    public void showSnackbar(final String msg) {
        Snackbar.make(dataView, msg, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    public void sendData() {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "http://5c8b765b.ngrok.io/api/storage/1?format=json";

        // Request a string response from the provided URL.
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
//                        try {
                            Log.d(TAG, response.toString());
//                        } catch (JSONException e) {
//                            Log.e(TAG, e.getMessage());
//                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, error.getMessage());
            }
        });
        // Add the request to the RequestQueue.
        queue.add(jsonObjectRequest);
    }
}
