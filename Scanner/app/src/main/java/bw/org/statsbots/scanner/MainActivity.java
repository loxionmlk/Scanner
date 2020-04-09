package bw.org.statsbots.scanner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private SurfaceView surfaceView;
    private BarcodeDetector barcodeDetector;
    private CameraSource cameraSource;
    private static final int REQUEST_CAMERA_PERMISSION = 201;
    //This class provides methods to play DTMF tones
    private ToneGenerator toneGen1;
    private TextView barcodeText;
    private String barcodeData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /***
         * acquire director permission to read and write
         * external storage
         */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC,     100);
        surfaceView = findViewById(R.id.surface_view);
        barcodeText = findViewById(R.id.barcode_text);
    }

    /**
     * Initializes and starts the scanner
     */
    private void initialiseDetectorsAndSources() {
        barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.ALL_FORMATS)
                .build();

        cameraSource = new CameraSource.Builder(this, barcodeDetector)
                .setRequestedPreviewSize(1920, 1080)
                .setAutoFocusEnabled(true) //you should add this feature
                .build();


        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                        cameraSource.start(surfaceView.getHolder());
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this, new
                                String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }


            }



            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                cameraSource.stop();
            }
        });


        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {

            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {

                final SparseArray<Barcode> barcodes = detections.getDetectedItems();
                Runnable r ;
                if (barcodes.size() != 0) {
                    barcodeText.post(r = new Runnable() {
                        @Override
                        public void run() {
                            barcodeData = barcodes.valueAt(0).displayValue;
                            barcodeText.setText(barcodeData);

                            if(barcodeText.getText().equals("")){
                                //keep retrying......
                            }else{

                                new Handler().removeCallbacks(this);
                                toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                                processResponse(this);
                                barcodeDetector.release();

                            }

                        }
                    });

                }

            }
        });
    }

    /**
     * Processes the scanned barcode, takes Runnable object parameter
     * @param r
     */
    public void processResponse(Runnable r){

        final Runnable rr= r;

        AlertDialog.Builder builderErr = new AlertDialog.Builder(MainActivity.this);
        builderErr.setTitle("Confirm Barcode");
        builderErr.setMessage("Scanned Barcode: "+barcodeText.getText());
        builderErr.setPositiveButton("Confirm", new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int id){
                //Write file to CSPRO Directory
                try {
                    int save_results = save(barcodeText.getText().toString());
                    if (save_results == 1){
                        finishAffinity();
                        System.exit(0);
                    }
                    else
                    {
                       AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                       alertDialog.setTitle("Error");
                       alertDialog.setMessage("Error encountered  while writing barcode to file");
                       alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                  public void onClick(DialogInterface dialog, int which) {
                                     dialog.dismiss();
                                  }
                            });
                       alertDialog.show();


                    }

                }catch(Exception e){
                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle("Error");
                    alertDialog.setMessage("Error encountered  while closing Scanner app");
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            }
        });
        builderErr.setNegativeButton("Rescan",new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int id){
                //retry scanning
                barcodeText.setText("");
                initialiseDetectorsAndSources();

            }
        });

        /**
         * VIBRATE DEVICE
         */
        Vibrator vibs = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vibs.vibrate(100);
        builderErr.show();



    }

    /***
     * write barcode to temporary file in COVID19 directory
     * ***/
    public int save(String contents) {
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Environment.getExternalStorageDirectory().getPath()+"/csentry/COVID19/tempBarcode/barcode.txt")));
            bw.write(contents);
            bw.close();
            return 1;
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSupportActionBar().hide();
        cameraSource.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSupportActionBar().hide();
        initialiseDetectorsAndSources();
    }



}
