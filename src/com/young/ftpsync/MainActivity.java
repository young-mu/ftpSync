package com.young.ftpsync;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import android.app.Activity;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity implements OnClickListener {
    private static final String TAG = "ftpSync";
    private Context mContext;

    private EditText hostEt;
    private EditText portEt;
    private EditText usernameEt;
    private EditText passwordEt;
    private Button connectBtn;
    private Button selectBtn;
    private Button uploadBtn;
    private TextView pathTv;
    private ImageView image;

    private FTPClient ftpClient = null;
    private Uri imgUri = null;
    private String imgPath = null;
    private String imgName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // get context
        mContext = MainActivity.this;
        // get widgets
        hostEt = (EditText)findViewById(R.id.host_edittext);
        portEt = (EditText)findViewById(R.id.port_edittext);
        usernameEt = (EditText)findViewById(R.id.username_edittext);
        passwordEt = (EditText)findViewById(R.id.password_edittext);
        connectBtn = (Button)findViewById(R.id.connect_button);
        selectBtn = (Button)findViewById(R.id.select_button);
        uploadBtn = (Button)findViewById(R.id.upload_button);
        pathTv = (TextView)findViewById(R.id.path_textview);
        image = (ImageView)findViewById(R.id.selimg_imageview);
        // set listeners
        connectBtn.setOnClickListener(this);
        uploadBtn.setOnClickListener(this);
        selectBtn.setOnClickListener(this);
        pathTv.setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (ftpClient != null) {
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
        case R.id.connect_button:
            Log.i(TAG, "click connect_button");
            String host = hostEt.getText().toString();
            int port = Integer.parseInt(portEt.getText().toString());
            String username = usernameEt.getText().toString();
            String password = passwordEt.getText().toString();
            Log.i(TAG, "host = " + host);
            Log.i(TAG, "port = " + port);
            Log.i(TAG, "username = " + username);
            Log.i(TAG, "password = " + password);
            ftpConnect connect = new ftpConnect(host, port, username, password);
            connect.start();
            break;
        case R.id.select_button:
            Log.i(TAG, "click select_button");
            image.setImageBitmap(null);
            Intent getAlbum = new Intent(Intent.ACTION_PICK, Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(getAlbum, 0);
            break;
        case R.id.upload_button:
            Log.i(TAG, "click upload_button");
            ftpUpload upload = new ftpUpload(imgPath, imgName);
            upload.start();
            break;
        case R.id.path_textview:
            Log.i(TAG, "click path_textview");
            image.setImageBitmap(null);
            Bitmap bm = null;
            try {
                bm = BitmapFactory.decodeStream(getContentResolver().openInputStream(imgUri));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            image.setImageBitmap(bm);
            break;
        default:
            break;
        }
    }

    class ftpConnect extends Thread {
        private String host;
        private int port;
        private String username;
        private String password;

        public ftpConnect(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        @Override
        public void run() {
            ftpClient = new FTPClient();
            try {
                ftpClient.connect(this.host, this.port);
                ftpClient.login(this.username, this.password);
                int reply = ftpClient.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftpClient.disconnect();
                    Log.e(TAG, "login failed!");
                } else {
                    Log.i(TAG, "login successfully!");
                }
                ftpClient.changeWorkingDirectory("ftp/Sync");
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            Log.e(TAG, "resultCode error!");
            return;
        }
        if (requestCode == 0) {
            imgUri = data.getData();
            imgPath = getRealPathFromURI(imgUri);
            imgName = getNameFromPath(imgPath);
            Log.i(TAG, "selected image URI : " + imgUri);
            Log.i(TAG, "selected image file path : " + imgPath);
            Log.i(TAG, "selected image name : " + imgName);
            pathTv.setText(imgName);
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        CursorLoader loader = new CursorLoader(mContext, contentUri, proj, null, null, null);
        Cursor cursor = loader.loadInBackground();
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String filepath = cursor.getString(column_index);
        cursor.close();
        return filepath;
    }

    private String getNameFromPath(String filepath) {
        int start = filepath.lastIndexOf("/");
        if (start != -1) {
            return filepath.substring(start+1);
        } else {
            return null;
        }
    }

    class ftpUpload extends Thread {
        FileInputStream fis;
        String remotefile;

        public ftpUpload(String localfile, String remotefile) {
            this.remotefile = remotefile;
            try {
                this.fis = new FileInputStream(localfile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                if (ftpClient == null) {
                    return;
                }
                ftpClient.storeFile(remotefile, fis);
                int reply = ftpClient.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    Log.e(TAG, "upload failed!");
                } else {
                    Log.i(TAG, "upload successfully!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
