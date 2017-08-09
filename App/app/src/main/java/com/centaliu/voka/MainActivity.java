package com.centaliu.voka;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.os.Handler;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import org.json.JSONException;
import org.json.JSONObject;

//====================================================================================================
//history:
//  2017.08.09 15:31 adjust the sequence of initialization, get DB password first, then Facebook login
//                   then start the game, due to need DB access while do facebook login
//====================================================================================================

public class MainActivity extends AppCompatActivity {
    private class vocData {
        private int vocid;
        private String voc = "";
        private String voctag;
        private String vocurl;
        private Boolean starword = false;
    }

    private int guessedNum = 0;//how many times user has guessed
    private int wrongGuess = 0;
    private boolean onPlaying = false;//indicate if the guessing game is on going
    private int userID = 0;
    private String usereMail = "";
    private vocData theVoc = new vocData();//save data of a vocabulary
    private int totVocItems = 0;
    private String tagsStr = "";
    private String dictStr = "";
    private int MAXWIDTH = 0;
    private int MAXHEIGHT = 0;
    private String FBUserName = "";//to save current facebook user name
    private String DBPWD = "";//to save the password of the database

    //LoginButton loginButton;
    CallbackManager callbackManager;

    public interface OnAyncTaskCompleted {
        void onAysncTaskCompleted(Object object);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d("DBG", "going to call getDBPWD()");
                    getDBPWD();
                }
                catch (Exception ex){
                    Log.d("DBG", "exception=" + ex.getMessage());
                }
            }
        }, 1000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    //get a preference data by its key.
    private String getPrefData(String Key)
    {
        String ret = "";
        SharedPreferences SP = getSharedPreferences("userdetails", MODE_PRIVATE);
        ret = SP.getString(Key, "");
        return ret;
    }

    //to save data to Shared Preferences
    private void savePref(String Key, String Val)
    {
        SharedPreferences SPData = getSharedPreferences("userdetails", MODE_PRIVATE);
        SharedPreferences.Editor edit = SPData.edit();
        edit.clear();
        Map<String, ?> all = SPData.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            edit.putString(entry.getKey(), entry.getValue().toString());
        }
        edit.putString(Key, Val);
        edit.commit();
    }

    //function to get password of remote database, return empty string if couldn't get it correctly
    public void getDBPWD()
    {
        final EditText PWDinput = new EditText(this);//an edittext text box for user to input password of remote database
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);//an object ot show a dialog for user to input something
        builder.setTitle("Please input password of remote database");
        PWDinput.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(PWDinput);
        //setup the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DBPWD = PWDinput.getText().toString();
                RemoteAccess testpwd = new RemoteAccess(new OnAyncTaskCompleted() {
                    public void onAysncTaskCompleted(Object object) {
                        ArrayList<Object> retArr = (ArrayList<Object>)object;
                        String retData = (String)retArr.get(0);
                        if (retData.equals("OK")) {
                            savePref("DBPWD", DBPWD);
                            //startGame();
                            FBLogin();
                        }
                        else {
                            finish();
                            System.exit(0);
                        }
                    }
                });
                testpwd.execute(new Object[]{8, DBPWD});
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                finish();
                System.exit(0);
            }
        });
        //get DBPWD from preference, if empty string, than pop up an input to ask user to input password of database
        DBPWD = getPrefData("DBPWD");
        Log.d("DBG", "getPrefData DBPWD =" + DBPWD);
        if (DBPWD.equals("")) builder.show();
        else {
            RemoteAccess testpwd = new RemoteAccess(new OnAyncTaskCompleted() {
                public void onAysncTaskCompleted(Object object) {
                    ArrayList<Object> retArr = (ArrayList<Object>)object;
                    String retData = (String)retArr.get(0);
                    if (retData.equals("OK")) FBLogin(); else builder.show();
                }
            });
            testpwd.execute(new Object[]{8, DBPWD});
        }
    }

    //function to do FB login
    public void FBLogin()
    {
        //0.iniitilzation of variables
        final float fScale = getResources().getDisplayMetrics().density;
        final int scale = Math.round(fScale * 100);//because sometimes fScale isn't integer, such as 1.5.
        MAXWIDTH = Math.round(getResources().getDisplayMetrics().widthPixels / fScale);
        MAXHEIGHT = Math.round(getResources().getDisplayMetrics().heightPixels / fScale);
        //0.1.declare variables of layouts
        final RelativeLayout rlTitle = (RelativeLayout)findViewById(R.id.activity_title);
        final RelativeLayout rlMain = (RelativeLayout)findViewById(R.id.activity_main);
        final RelativeLayout rlTags = (RelativeLayout)findViewById(R.id.activity_tags);
        //0.2.a reusable layoutParams variable
        RelativeLayout.LayoutParams layout = null;
        final TextView tvHello = new TextView(this);

        //add a facebook login button in the Title layout
        final com.facebook.login.widget.LoginButton loginButton = new com.facebook.login.widget.LoginButton(this);
        callbackManager = CallbackManager.Factory.create();
        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d("DBG", "onSuccess:" + loginResult.getAccessToken().getUserId());
                GraphRequest request = GraphRequest.newMeRequest(loginResult.getAccessToken(),
                        new GraphRequest.GraphJSONObjectCallback() {
                            @Override
                            public void onCompleted(JSONObject object, GraphResponse response) {
                                try {
                                    FBUserName = object.getString("name");
                                    tvHello.setText("Hello " + FBUserName);
                                    savePref("FBUserName", FBUserName);
                                }
                                catch (JSONException ex) {
                                    Log.d("DBG", "JSONException=" + ex.getMessage());
                                }
                            }
                        }
                );
                Bundle bundle = new Bundle();
                bundle.putString("fields", "id,name");
                request.setParameters(bundle);
                request.executeAsync();
                RemoteAccess access = new RemoteAccess(new OnAyncTaskCompleted() {
                    public void onAysncTaskCompleted(Object object) {
                        ArrayList<Object> ret = (ArrayList<Object>)object;
                        String retData = (String)ret.get(0);
                        String[] sepData = retData.split(",");
                        userID = Integer.parseInt(sepData[0]);
                        rlTags.setVisibility(View.VISIBLE);
                        rlTitle.setVisibility(View.INVISIBLE);
                        startGame();
                    }
                });
                access.execute(new Object[]{0, "" + loginResult.getAccessToken().getUserId()});
            }
            @Override
            public void onCancel() {
                Log.d("DBG", "onCancel, do nothing");
            }
            @Override
            public void onError(FacebookException error) {
                Log.d("DBG", "onError");
            }
        });
        loginButton.setVisibility(View.INVISIBLE);
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        rlTitle.addView(loginButton, layout);
        //add an imageview to show logo
        final ImageView imgLogo = new ImageView(this);
        imgLogo.setVisibility(View.VISIBLE);
        imgLogo.setImageResource(R.drawable.vokalogo);
        imgLogo.setVisibility(View.INVISIBLE);
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        layout.topMargin = (scale * 80) / 100;
        rlTitle.addView(imgLogo, layout);
        //add a button to log out facebook account
        final Button btnLogout = new Button(this);
        btnLogout.setText("Logout");
        btnLogout.setPadding(1, 1, 1, 1);
        btnLogout.setTextColor(Color.WHITE);
        btnLogout.setBackgroundColor(Color.rgb(72, 98, 163));
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //login out current account
                LoginManager.getInstance().logOut();
                //show the logo and facebook button for user to login
                loginButton.setVisibility(View.VISIBLE);
                imgLogo.setVisibility(View.VISIBLE);
                rlTags.setVisibility(View.INVISIBLE);
                rlTitle.setVisibility(View.VISIBLE);
            }
        });
        btnLogout.setVisibility(View.INVISIBLE);
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
        layout.topMargin = (scale * 20) / 100;
        layout.height = (scale * 30) / 100;
        rlTags.addView(btnLogout, layout);
        //a textview to greet user
        tvHello.setTextColor(Color.WHITE);
        tvHello.setTextSize(28.0f);
        tvHello.setText("Hello");
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.leftMargin = (scale * 20) / 100;
        layout.topMargin = (scale * 20) / 100;
        rlTags.addView(tvHello, layout);

        //0.3.check if currently FB logined.
        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        if (accessToken != null) {
            //update the lastupdate field and get user id from remote
            RemoteAccess access = new RemoteAccess(new OnAyncTaskCompleted() {
                public void onAysncTaskCompleted(Object object) {
                    ArrayList<Object> ret = (ArrayList<Object>)object;
                    String retData = (String)ret.get(0);
                    String[] sepData = retData.split(",");
                    userID = Integer.parseInt(sepData[0]);
                    rlTags.setVisibility(View.VISIBLE);
                    rlTitle.setVisibility(View.INVISIBLE);
                    startGame();
                }
            });
            access.execute(new Object[]{0, "" + accessToken.getUserId()});
            tvHello.setText("Hello " + getPrefData("FBUserName"));
            btnLogout.setVisibility(View.VISIBLE);
        }  else {
            //show the logo and facebook button for user to login
            loginButton.setVisibility(View.VISIBLE);
            imgLogo.setVisibility(View.VISIBLE);
        }


        //8.hide main operation layout at luanching APP
        rlMain.setVisibility(View.INVISIBLE);
        rlTags.setVisibility(View.INVISIBLE);
    }

    //function for starting the game once everything is ready
    public void startGame() {
        final float fScale = getResources().getDisplayMetrics().density;
        final int scale = Math.round(fScale * 100);//because sometimes fScale isn't integer, such as 1.5.
        final RelativeLayout rlMain = (RelativeLayout)findViewById(R.id.activity_main);
        final RelativeLayout rlTags = (RelativeLayout)findViewById(R.id.activity_tags);
        RelativeLayout.LayoutParams layout = null;
        final Button[] btnLetter = new Button[26];
        for (int i=0; i<26; i++) btnLetter[i] = new Button(this);
        final String[] letters = {"a", "e", "i", "o", "u", "b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "q", "r", "s", "t", "v", "w", "x", "y", "z"};
        final TextView[] chs = new TextView[20];
        for (int i=0; i<20; i++) chs[i] = new TextView(this);
        final List<vocData> vocs = new ArrayList<vocData>();
        final Button btnNew = new Button(this);
        final Button btnSound = new Button(this);
        final Button btnDict = new Button(this);
        final TextView tvDescription = new TextView(this);
        final ImageView imgHint = new ImageView(this);
        final ImageView imgStar = new ImageView(this);
        final ImageView[] errLight =  new ImageView[3];


        //5.get all tag names and show as checkboxes in the rltags
        final CheckBox[] chkTag = new CheckBox[20];
        final TextView tvTagDesc = new TextView(this);
        RemoteAccess getTag = new RemoteAccess(new OnAyncTaskCompleted() {
            public void onAysncTaskCompleted(Object object) {
                ArrayList<Object> ret = (ArrayList<Object>)object;
                String retData = (String)ret.get(0);
                String[] sepData = retData.split("~");
                RelativeLayout.LayoutParams tagLayout = null;
                String savedTags = getPrefData("tags");
                for (int i = 0; i < sepData.length; i++) {
                    chkTag[i] = new CheckBox(getApplicationContext());
                    chkTag[i].setText(sepData[i]);
                    chkTag[i].setTag(sepData[i]);
                    chkTag[i].setTextColor(Color.BLACK);
                    if (savedTags.equals("")) chkTag[0].setChecked(true);
                    else if (savedTags.contains(chkTag[i].getText().toString())) chkTag[i].setChecked(true);
                    tagLayout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, 30 * scale / 100);
                    if (i % 2 == 0) tagLayout.leftMargin = (scale * 50) / 100;
                    else tagLayout.leftMargin = (scale * 250) / 100;
                    tagLayout.topMargin = (scale * 110 + 12000 * (i / 2)) / 100;
                    rlTags.addView(chkTag[i], tagLayout);
                }
            }
        });
        getTag.execute(new Object[]{2, ""});
        //6.add a description to rlTags to guide user to select categories
        tvTagDesc.setText("Please select categories...");
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.leftMargin = (scale * 20) / 100;
        layout.topMargin = (scale * 80) / 100;
        rlTags.addView(tvTagDesc, layout);
        //7.add a button named btnLoad to start the game with these tags
        final Button btnLoad = new Button(this);
        btnLoad.setText("Load");
        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tagsStr = "";
                for (int i = 0; i < 20; i++) {
                    if (chkTag[i] != null)  {
                        if (chkTag[i].isChecked()) {
                            if (tagsStr == "") tagsStr = chkTag[i].getText().toString(); else tagsStr = tagsStr + "," + chkTag[i].getText();
                        }
                    }
                }
                if (tagsStr.equals("")) Toast.makeText(getApplicationContext(), "You have to select at least one category!!", Toast.LENGTH_LONG).show();
                else {
                    savePref("tags", tagsStr);
                    rlTags.setVisibility(View.INVISIBLE);
                    rlMain.setVisibility(View.VISIBLE);
                }
            }
        });
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.leftMargin = (scale * 150) / 100;
        layout.topMargin = (scale * 450) / 100;
        rlTags.addView(btnLoad, layout);

        //1.add 26 letters as buttons for clicking
        for (int i=0; i<26; i++)  {
            btnLetter[i].setText(letters[i].toString());
            btnLetter[i].setVisibility(View.VISIBLE);
            btnLetter[i].setPadding(0, 0 ,0, 0);
            //layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, 30 * scale / 100);
            if (i>=0 && i<=4) {
                layout.leftMargin = (scale * 35 * i + 4400) / 100;
                layout.topMargin = (scale * 110) / 100;
            } else if (i>=5 && i<=11) {
                layout.leftMargin = (scale * 35 * (i - 5) + 4400) / 100;
                layout.topMargin = (scale * 160) / 100;
            } else if (i>=12 && i<=18) {
                layout.leftMargin = (scale * 35 * (i -12) + 4400) / 100;
                layout.topMargin = (scale * 210) / 100;
            } else {
                layout.leftMargin = (scale * 35 * (i -19) + 4400) / 100;
                layout.topMargin = (scale * 260) / 100;
            }
            layout.width = (scale * 10 * 3) / 100;
            btnLetter[i].setGravity(Gravity.CENTER | Gravity.CENTER_VERTICAL);
            btnLetter[i].setBackgroundColor(Color.LTGRAY);
            btnLetter[i].setTextColor(Color.BLACK);
            btnLetter[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!onPlaying) return;
                    // one set of playing ends either all letters are guessed or got three wrong guesses
                    Boolean oneSetEnds = false;
                    Button theBtn = (Button)v;
                    //theBtn.getCurrentTextColor();
                    ColorDrawable btnColor = (ColorDrawable) theBtn.getBackground();
                    int colorId = theBtn.getCurrentTextColor();
                    if (colorId == -16777216) {
                        boolean isIncluded = false;
                        theBtn.setTextColor(Color.parseColor("#606060"));
                        theBtn.setBackgroundColor(Color.parseColor("#9DFF7F"));
                        for (int i=0; i<theVoc.voc.length(); i++) {
                            int asciiClicked = theBtn.getText().charAt(0);
                            int asciiVoc = theVoc.voc.substring(i, i+1).charAt(0);
                            if (asciiClicked == asciiVoc) {
                                isIncluded = true;
                                chs[i].setText(theBtn.getText());
                                guessedNum++;
                            }
                        }
                        if (!isIncluded) {
                            wrongGuess++;
                            //change the background color if the letter is not included
                            //theBtn.setBackgroundColor(Color.parseColor("#FFE0B2"));
                            theBtn.setBackgroundColor(Color.parseColor("#FF5F56"));
                            for (int i = 0; i < wrongGuess; i++) errLight[i].setImageResource(R.drawable.red);
                            //maximal accepted wrong guess is 3
                            if (wrongGuess == 3) {
                                //reveal the answer
                                for (int i=0; i<theVoc.voc.length(); i++) {
                                    if (chs[i].getText().equals("_"))  {
                                        chs[i].setText(theVoc.voc.substring(i, i + 1));
                                        chs[i].setTextColor(Color.RED);
                                    }
                                }
                                oneSetEnds = true;
                            }
                        }
                        //check if all letters are guessed correctly
                        if (theVoc.voc.length() == guessedNum) {
                            if (wrongGuess == 0) Toast.makeText(getApplicationContext(), "Perfect, you got the answer!", Toast.LENGTH_LONG).show();
                            if (wrongGuess == 1) Toast.makeText(getApplicationContext(), "Great, that's correct!", Toast.LENGTH_LONG).show();
                            if (wrongGuess == 2) Toast.makeText(getApplicationContext(), "Good, you did it!", Toast.LENGTH_LONG).show();
                            oneSetEnds = true;
                        }
                        if (oneSetEnds)  {
                            onPlaying = false;
                            btnNew.setEnabled(true);
                            btnDict.setEnabled(true);
                            imgStar.setVisibility(View.VISIBLE);
                            if (theVoc.starword) imgStar.setImageResource(R.drawable.markon); else  imgStar.setImageResource(R.drawable.markoff);
                            //record a log
                            RemoteAccess logIt = new RemoteAccess(new OnAyncTaskCompleted() {
                                public void onAysncTaskCompleted(Object object) {
                                    ArrayList<Object> ret = (ArrayList<Object>)object;
                                }
                            });
                            logIt.execute(new Object[]{7, wrongGuess == 3 ? 1 : 0, wrongGuess});
                        }
                    }
                }
            });
            rlMain.addView(btnLetter[i], layout);
        }
        //2.add separators
        final View vSep1 = new View(this);
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 5);
        layout.leftMargin = (scale * 14) / 100;
        layout.topMargin = (scale * 100) / 100;
        vSep1.setBackgroundColor(Color.parseColor("#000000"));
        rlMain.addView(vSep1, layout);
        //3.setup and add the New button, user clicks it to get a vocabulary for this question
        btnNew.setText("New");
        btnNew.setVisibility(View.VISIBLE);
        btnNew.setEnabled(false);
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.leftMargin = (scale * 10) / 100;
        layout.topMargin = (scale * 50) / 100;
        btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //1.disable the buttons for preventing double click
                btnNew.setEnabled(false);
                btnDict.setEnabled(false);
                btnSound.setEnabled(false);
                //2.hide the photo of preview of the word
                imgHint.setVisibility(View.INVISIBLE);
                imgStar.setVisibility(View.INVISIBLE);
                //4.hide the descrition
                tvDescription.setVisibility(View.INVISIBLE);
                //5.show letters to be clicked
                for (int i=0; i<26; i++) {
                    btnLetter[i].setVisibility(View.VISIBLE);
                    btnLetter[i].setBackgroundColor(Color.LTGRAY);
                    btnLetter[i].setTextColor(Color.BLACK);
                }
                //6.disable the btnDict and btnsound button, unmark the imgStar
                imgStar.setImageResource(R.drawable.markoff);
                imgStar.setTag("" + 0);
                //7.remove last textviews from layout
                if (theVoc != null) {
                    for (int i = 0; i < theVoc.voc.length(); i++) {
                        if (chs[i] != null) {
                            RelativeLayout tmpTV = (RelativeLayout) chs[i].getParent();
                            tmpTV.removeView(chs[i]);
                        }
                    }
                }
                //8.get random word for testing remotely
                RemoteAccess getVoc = new RemoteAccess(new OnAyncTaskCompleted() {
                    public void onAysncTaskCompleted(Object object) {
                        String retData = "";
                        ArrayList<Object> ret = (ArrayList<Object>)object;
                        try {
                            retData = (String)ret.get(0);
                        }
                        catch (IndexOutOfBoundsException ex)
                        {
                            Log.d("DBG", "IndexOutOfBoundsException on asynced task of loading the random vocabulary");
                            Toast.makeText(getApplicationContext(), "Loading error, please click the 'New' button again!!", Toast.LENGTH_LONG).show();
                            btnNew.setEnabled(true);
                            return;
                        }
                        if (!retData.equals("")) {
                            if (retData.equals("IOException")) {
                                Toast.makeText(getApplicationContext(), "Loading error, please click the 'New' button again!!", Toast.LENGTH_LONG).show();
                                btnNew.setEnabled(true);
                                return;
                            }
                            //3.set all errot lights back to dark red
                            for (int i=0; i<errLight.length; i++) {
                                errLight[i].setVisibility(View.VISIBLE);
                                errLight[i].setImageResource(R.drawable.reddark);
                            }
                            String[] sepData = retData.split("~");
                            theVoc = new vocData();
                            theVoc.vocid = Integer.parseInt(sepData[0]);
                            Log.d("DBG", "VOCID=" + theVoc.vocid);
                            theVoc.voc = sepData[1];
                            theVoc.vocurl = sepData[2];
                            if (Integer.parseInt(sepData[3]) == 1) theVoc.starword = true; else theVoc.starword = false;
                            for (int i=0; i<theVoc.voc.length();i++) {
                                chs[i].setTextColor(Color.GREEN);
                                chs[i].setText("_");
                                chs[i].setGravity(Gravity.CENTER | Gravity.BOTTOM);
                                chs[i].setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                                chs[i].setTextSize(32.0f);
                                chs[i].setVisibility(View.VISIBLE);
                                RelativeLayout.LayoutParams layoutX = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                layoutX.leftMargin = (scale * (24 * (i+1) + 20)) / 100;
                                layoutX.topMargin = (scale * 10) / 100;
                                rlMain.addView(chs[i], layoutX);
                            }
                            //initilization of a new round of guessing
                            btnSound.setEnabled(true);
                            guessedNum = 0;
                            wrongGuess = 0;
                            onPlaying = true;
                        }
                        else {
                            theVoc = null;
                            Log.d("DBG", "error while getting new vocabulary.");
                            Toast.makeText(getApplicationContext(), "Loading error, please click the 'New' button again!!", Toast.LENGTH_LONG).show();
                            btnNew.setEnabled(true);
                            return;
                        }
                        //imgStar.setVisibility(View.VISIBLE);
                        btnSound.setEnabled(true);
                        //get the photo bitmap of this word from fotolia.com and show in imageview
                        getPhoto getphoto = new getPhoto(new OnAyncTaskCompleted() {
                            public void onAysncTaskCompleted(Object object) {
                                ArrayList<Object> ret = (ArrayList<Object>)object;
                                Bitmap bitmapHint = null;
                                try {
                                    bitmapHint = (Bitmap)ret.get(0);
                                }
                                catch (IndexOutOfBoundsException ex)
                                {
                                    Log.d("DBG", "IndexOutOfBoundsException on asynced task of loading the photo from fotolia.com");
                                    Toast.makeText(getApplicationContext(), "Loading error, please click the 'New' button again!!", Toast.LENGTH_LONG).show();
                                    btnNew.setEnabled(true);
                                    return;
                                }
                                imgHint.setImageBitmap(bitmapHint);
                                imgHint.setVisibility(View.VISIBLE);
                            }
                        });
                        getphoto.execute(new Object[]{theVoc.voc});
                        //get the meaning of this voc from DB and store in a varible
                        RemoteAccess getDict = new RemoteAccess(new OnAyncTaskCompleted() {
                            public void onAysncTaskCompleted(Object object) {
                                ArrayList<Object> ret = (ArrayList<Object>)object;
                                try {
                                    dictStr = (String)ret.get(0);
                                }
                                catch (IndexOutOfBoundsException ex)
                                {
                                    Log.d("DBG", "IndexOutOfBoundsException on asynced task of loading the dict description from 000webhost.com");
                                    Toast.makeText(getApplicationContext(), "Loading error, please click the 'New' button again!!", Toast.LENGTH_LONG).show();
                                    btnNew.setEnabled(true);
                                    return;
                                }
                            }
                        });
                        getDict.execute(new Object[]{4, theVoc.voc});

                    }
                });
                //9.get a category randomly, for getting the
                Random rnd = new Random();
                String[] arrTags = tagsStr.split(",");
                int nextTagInt = rnd.nextInt(arrTags.length);
                String ranTag = arrTags[rnd.nextInt(arrTags.length)];
                getVoc.execute(new Object[]{3, ranTag});
            }
        });
        rlMain.addView(btnNew, layout);
        //4.add a button for playing pronounciation of the word
        btnSound.setText("Listen");
        btnSound.setVisibility(View.VISIBLE);
        btnSound.setEnabled(false);
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.leftMargin = (scale * 110) / 100;
        layout.topMargin = (scale * 50) / 100;

        btnSound.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                btnSound.setEnabled(false);
                String MP3URL = "https://s.yimg.com/tn/dict/dreye/live/" + theVoc.vocurl + "/" + theVoc.voc + ".mp3";
                if (theVoc.vocurl.equals("f1") || theVoc.vocurl.equals("m1")) MP3URL = "https://s.yimg.com/tn/dict/dreye/live/" + theVoc.vocurl.replace("1", "") + "/" + theVoc.voc + "@1.mp3";
                if (theVoc.vocurl.equals("F") || theVoc.vocurl.equals("M")) MP3URL = "https://s.yimg.com/tn/dict/dreye/live/" + theVoc.vocurl.toLowerCase() + "/" + theVoc.voc.substring(0, 1).toUpperCase() + theVoc.voc.substring(1) + ".mp3";
                MP3Player mp3Player = new MP3Player(new OnAyncTaskCompleted() {
                    public void onAysncTaskCompleted(Object object) {
                        ArrayList<Object> ret = (ArrayList<Object>)object;
                        btnSound.setEnabled(true);
                    }
                });
                mp3Player.execute(new Object[]{MP3URL});
            }
        });

        rlMain.addView(btnSound, layout);
        //5.add a button for querying definition from vocabularies.com
        btnDict.setText("Dict");
        btnDict.setVisibility(View.VISIBLE);
        btnDict.setEnabled(false);
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.leftMargin = (scale * 220) / 100;
        layout.topMargin = (scale * 50) / 100;
        btnDict.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                //show the meaning of this word, got from vocabulary.com
                for (int i = 0; i < btnLetter.length; i++) btnLetter[i].setVisibility(View.INVISIBLE);
                for (int i = 0; i < errLight.length; i++) errLight[i].setVisibility(View.INVISIBLE);
                dictStr = dictStr.replace("[]", " ");
                dictStr = dictStr.replace("[0x39]", "'");
                dictStr = dictStr.replace("[0x34]", "\"");
                tvDescription.setText(dictStr);
                tvDescription.setVisibility(View.VISIBLE);
            }
        });
        rlMain.addView(btnDict, layout);
        //6.add a imageView for showing related image
        imgHint.setVisibility(View.VISIBLE);
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.leftMargin = (scale * 20) / 100;
        layout.topMargin = (scale * 320) / 100;
        layout.width = (scale * (MAXWIDTH - 20)) / 100;
        layout.height = (scale * MAXHEIGHT) / 100;
        rlMain.addView(imgHint, layout);
        //7.create a Textview to show meaning of a vocabulary
        tvDescription.setText("");
        tvDescription.setVisibility(View.INVISIBLE);
        tvDescription.setTextSize(20.0f);
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.leftMargin = (scale * 14) / 100;
        layout.topMargin = (scale * 110) / 100;
        rlMain.addView(tvDescription, layout);
        //8.add three red lights to show how many errors so far.
        for (int i=0; i<errLight.length; i++) {
            errLight[i] = new ImageView(this);
            errLight[i].setImageResource(R.drawable.reddark);
            errLight[i].setVisibility(View.INVISIBLE);
            layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            layout.leftMargin = (scale * 320) / 100;
            layout.topMargin = (scale * (120 + 45 *i)) / 100;
            rlMain.addView(errLight[i], layout);
        }
        btnNew.setEnabled(true);
        //show the star mark in front of the word that waiting for guessing
        imgStar.setVisibility(View.INVISIBLE);
        imgStar.setImageResource(R.drawable.markoff);
        imgStar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ImageView star = (ImageView)v;
                if (!theVoc.starword) {
                    star.setImageResource(R.drawable.markon);
                    theVoc.starword = true;
                    //insert this word as a stared word to database
                    RemoteAccess starwordIns = new RemoteAccess(new OnAyncTaskCompleted() {
                        public void onAysncTaskCompleted(Object object) {
                            ArrayList<Object> ret = (ArrayList<Object>)object;
                        }
                    });
                    starwordIns.execute(new Object[]{5});
                }
                else {
                    star.setImageResource(R.drawable.markoff);
                    theVoc.starword = false;
                    //delete this word from stared word to database
                    RemoteAccess starwordDel = new RemoteAccess(new OnAyncTaskCompleted() {
                        public void onAysncTaskCompleted(Object object) {
                            ArrayList<Object> ret = (ArrayList<Object>)object;
                        }
                    });
                    starwordDel.execute(new Object[]{6});
                }
            }
        });
        layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.leftMargin = (scale * 10) / 100;
        layout.topMargin = (scale * 10) / 100;
        rlMain.addView(imgStar, layout);
    }

    //for getting a picture related to the word from fotolia.com
    public class getPhoto extends AsyncTask {
        public OnAyncTaskCompleted delegate = null;

        public getPhoto(OnAyncTaskCompleted onAyncTaskCompleted) {
            delegate = onAyncTaskCompleted;
        }

        @Override
        protected List<Object> doInBackground(Object[] objects) {
            String theVocWord = (String)objects[0];
            List<Object> ret = new ArrayList<Object>();
            String resTitle = "";
            try {
                String retString = "";
                //1.search fotolia with keyword, try to get the very first image
                URL url = new URL("https://www.fotolia.com/search?k=" + theVocWord);
                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                String str = "";
                Random rnd = new Random();
                int pos = rnd.nextInt(5) + 1;
                while ((str = in.readLine()) != null) {
                    retString = retString + str;
                    if (str.indexOf("content-thumb:") > 0) {
                        if (pos != 1) retString = retString.replace("content-thumb:", ""); else break;
                        pos = pos - 1;
                    }
                }
                in.close();
                int idxDesc = retString.indexOf("content-thumb:");
                int idxDescEnd = retString.indexOf(">", idxDesc+14);
                String photoID = retString.substring(idxDesc + 14, idxDescEnd-1);
                //2.get the direct url of the photo from fotolia.
                url = new URL("https://www.fotolia.com/id/" + photoID);
                in = new BufferedReader(new InputStreamReader(url.openStream()));
                retString = "";
                while ((str = in.readLine()) != null) {
                    retString = retString + str;
                    if (str.indexOf("contentUrl") > 0) break;
                }
                idxDesc = retString.indexOf("contentUrl");
                idxDescEnd = retString.indexOf(">", idxDesc + 21);
                String photoURL = retString.substring(idxDesc + 21, idxDescEnd-3);
                //3.get image from the photo url to a bitmap to return back.
                url = new URL(photoURL);
                Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                ret.add(bmp);
            }
            catch (IOException ex) {

            }
            return ret;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            delegate.onAysncTaskCompleted(o);
        }
    }

    //for playing the pronounciation
    public class MP3Player extends AsyncTask {
        public OnAyncTaskCompleted delegate = null;

        public MP3Player(OnAyncTaskCompleted onAyncTaskCompleted) {
            delegate = onAyncTaskCompleted;
        }

        @Override
        protected List<Object> doInBackground(Object[] objects) {
            String theMP3URL = (String)objects[0];
            List<Object> ret = new ArrayList<Object>();
            //MediaPlayer mp = new MediaPlayer();
            MediaPlayer mp = MediaPlayer.create(MainActivity.this, Uri.parse(theMP3URL));
            mp.setVolume(1.0f, 1.0f);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) { }
            });
            try {
                mp.start();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Error on loading prounication, please try again.", Toast.LENGTH_LONG).show();
                if (isEmulator()) Toast.makeText(getApplicationContext(), "theVoc=" + theVoc, Toast.LENGTH_LONG).show();
            }
            return ret;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            delegate.onAysncTaskCompleted(o);
        }
    }

    //for getting or inserting data from/to remote DB via remote PHP files
    public class RemoteAccess extends AsyncTask {
        public OnAyncTaskCompleted delegate = null;

        public RemoteAccess(OnAyncTaskCompleted onAyncTaskCompleted) {
            delegate = onAyncTaskCompleted;
        }
        /*
            Input object array:
                0: Int, index to tell the task what's going to do here
            Return list
                0: String, vocabulary data with a certain user name from remote Webpage
        */
        @Override
        protected List<Object> doInBackground(Object[] objects) {
            List<Object> ret = new ArrayList<Object>();
            String rootURL = "http://inordinate-formatio.000webhostapp.com/voka/";
            try {
                Integer Idx = (Integer)objects[0];
                String remoteURL = rootURL;
                switch (Idx) {
                    case 0:
                        /* to logging in by using FBID, amd get back the UserID, isActive, and lastLogin Time, and also their starred words */
                        String facebookID = (String)objects[1];
                        remoteURL = remoteURL + "login.php?fbid=" + facebookID + "&dbpwd=" + DBPWD;
                        break;
                    case 1:
                        /* query vocabularies of this user, by user id*/
                        /* legacy way to get new voc, might have to set as obsolete*/
                        remoteURL = remoteURL + "getvoc.php?userid=" + userID + "&dbpwd=" + DBPWD;
                        break;
                    case 2:
                        /* query vocabularies of this user, by user id*/
                        remoteURL = remoteURL + "gettags.php" + "?dbpwd=" + DBPWD;
                        break;
                    case 3:
                        /* get the random word for testing, use tag as its parameter */
                        String tag = (String)objects[1];
                        remoteURL = remoteURL + "newrandomvoc.php?tag=" + tag + "&userid=" + userID + "&dbpwd=" + DBPWD;
                        break;
                    case 4:
                        /* get dict data from tblvocbank*/
                        String voc = (String)objects[1];
                        remoteURL = remoteURL + "getvocdict.php?voc=" + voc + "&dbpwd=" + DBPWD;
                        break;
                    case 5:
                        /* mark a word as a starred word */
                        remoteURL = remoteURL + "insertstarwords.php?userid=" + userID + "&voc=" + theVoc.voc + "&dbpwd=" + DBPWD;
                        break;
                    case 6:
                        /* unmark a starred word */
                        remoteURL = remoteURL + "delstarwords.php?userid=" + userID + "&voc=" + theVoc.voc + "&dbpwd=" + DBPWD;
                        break;
                    case 7:
                        /* unmark a starred word */
                        int gotit = (int)objects[1];
                        int wrongguess = (int)objects[2];
                        remoteURL = remoteURL + "insertlog.php?userid=" + userID + "&voc=" + theVoc.voc + "&gotit=" + gotit + "&wrongguess=" + wrongguess + "&dbpwd=" + DBPWD;
                        Log.d("DBG", remoteURL);
                        break;
                    case 8:
                        /* for testing if inputted password od database is correct */
                        String testingpwd = (String)objects[1];
                        remoteURL = remoteURL + "testconn.php?dbpwd=" + testingpwd;
                }
                // Create a URL for the desired page
                String retString = "";
                Log.d("DBG", "remoteURL: " + remoteURL);
                URL url = new URL(remoteURL);
                // Read all the text returned by the server
                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                String str;
                while ((str = in.readLine()) != null) retString = retString + str;
                in.close();
                ret.add(retString);
            } catch (MalformedURLException ex) {
                Log.d("DBG", "MalformedURLException:" + ex.getMessage());
            } catch (IOException ex) {
                Log.d("DBG", "IOException:" + ex.getMessage());
                ret.add("IOException");
            }
            return ret;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            delegate.onAysncTaskCompleted(o);
        }
    }

    //function to detect if the App is running on a real mobile device or emulator
    protected boolean isEmulator()
    {
        boolean ret = false;
        if (Build.MODEL.contains("Android SDK built for x86")) ret = true;
        if (Build.DEVICE.startsWith("generic")) ret = true;
        if (Build.MANUFACTURER.startsWith("unknown")) ret = true;
        return ret;
    }

}
