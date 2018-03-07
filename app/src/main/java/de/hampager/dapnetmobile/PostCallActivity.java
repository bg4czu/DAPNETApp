package de.hampager.dapnetmobile;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.tokenautocomplete.FilteredArrayAdapter;
import com.tokenautocomplete.TokenCompleteTextView;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.hampager.dap4j.models.CallSign;
import de.hampager.dap4j.DAPNETAPI;
import de.hampager.dap4j.models.CallResource;
import de.hampager.dap4j.ServiceGenerator;
import de.hampager.dap4j.models.TransmitterGroup;
import de.hampager.dapnetmobile.tokenautocomplete.CallsignsCompletionView;
import de.hampager.dapnetmobile.tokenautocomplete.TransmitterGroupCompletionView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PostCallActivity extends AppCompatActivity implements TokenCompleteTextView.TokenListener<CallSign> {
    private static final String TAG = "PostCallActivity";
    CallsignsCompletionView callSignsCompletion;
    TransmitterGroupCompletionView transmitterGroupCompletion;
    String server;
    String user;
    String password;
    private TextInputEditText message;
    //private EditText transmitterGroupNames
    private Boolean emergencyBool = false;
    private List<String> csnl = new ArrayList<>();
    private List<String> tgnl = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_call);
        SharedPreferences sharedPref = getSharedPreferences("sharedPref", Context.MODE_PRIVATE);
        server = sharedPref.getString("server", "http://www.hampager.de:8080");
        user = sharedPref.getString("user", "invalid");
        password = sharedPref.getString("pass", "invalid");
        if (user.equals("invalid")&&password.equals("invalid")&&server.equals("http://www.hampager.de:8080"))
            Snackbar.make(findViewById(R.id.postcallcoordinator), "You don't seem to be logged in.", Snackbar.LENGTH_LONG).show();
        Gson gson = new Gson();
        String callsignJson = sharedPref.getString("callsigns", "");
        CallSign[] callSignResources=gson.fromJson(callsignJson,CallSign[].class);
        if (callSignResources!=null){
            setCallsigns(callSignResources);
        }
        String transmitterJson = sharedPref.getString("transmitters","");
        TransmitterGroup[] transmitterGroupResources= gson.fromJson(transmitterJson,TransmitterGroup[].class);
        if (transmitterGroupResources!=null)
        setTransmittergroups(transmitterGroupResources);
        defineObjects();
        getCallsigns();
        getTransmitterGroups();
    }

    private void defineObjects() {
        message = (TextInputEditText) findViewById(R.id.post_call_text);
        Switch emergency = (Switch) findViewById(R.id.post_call_emergencyswitch);
        String m = user.toUpperCase();
        m += ": ";
        message.setText(m);
        message.requestFocus();
        emergency.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                emergencyBool = isChecked;
            }
        });
    }

    private void getCallsigns() {
        try {
            ServiceGenerator.changeApiBaseUrl(server);
        } catch (java.lang.NullPointerException e) {
            ServiceGenerator.changeApiBaseUrl("http://www.hampager.de:8080");
        }
        DAPNETAPI service = ServiceGenerator.createService(DAPNETAPI.class, user, password);
        Call<List<CallSign>> call;
        call = service.getCallSign("");
        call.enqueue(new Callback<List<CallSign>>() {
            @Override
            public void onResponse(Call<List<CallSign>> call, Response<List<CallSign>> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Connection getting Callsigns was successful");
                    // tasks available
                    List<CallSign> data = response.body();
                    Collections.sort(data, new Comparator<CallSign>() {
                        @Override
                        public int compare(CallSign o1, CallSign o2) {
                            int n = o1.getName().compareTo(o2.getName());
                            if (n==0)
                                return o1.getDescription().compareTo(o2.getDescription());
                            else
                                return n;
                        }
                    });
                    CallSign[] dataArray = data.toArray(new CallSign[data.size()]);
                    saveData(dataArray);
                    setCallsigns(dataArray);
                    //adapter = new CallAdapter(data);
                } else {
                    //APIError error = ErrorUtils.parseError(response);
                    Log.e(TAG, "Error " + response.code());
                    Log.e(TAG, response.message());
                    if (response.code() == 401) {
                        SharedPreferences sharedPref = PostCallActivity.this.getSharedPreferences("sharedPref", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.clear();
                        editor.apply();
                    }
                }
            }

            @Override
            public void onFailure(Call<List<CallSign>> call, Throwable t) {
                // something went completely wrong (e.g. no internet connection)
                Log.e(TAG, "Error... Do you have internet? "+ t.getMessage());
            }
        });
    }

    private void setCallsigns(CallSign[] data) {
        callSignsCompletion = (CallsignsCompletionView) findViewById(R.id.callSignSearchView);
        callSignsCompletion.setAdapter(generateAdapter(data));
        callSignsCompletion.setTokenListener(this);
        callSignsCompletion.setTokenClickStyle(TokenCompleteTextView.TokenClickStyle.Select);
        callSignsCompletion.allowDuplicates(false);
        callSignsCompletion.setThreshold(0);
        callSignsCompletion.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_FILTER|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        callSignsCompletion.performBestGuess(true);
    }
    private void saveData(CallSign[] input){
        SharedPreferences sharedPref = getSharedPreferences("sharedPref", Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = sharedPref.edit();
        Gson gson = new Gson();
        String json = gson.toJson(input);
        prefsEditor.putString("callsigns", json);
        prefsEditor.apply();
    }
    private void saveData(TransmitterGroup[] input){
        SharedPreferences sharedPref = getSharedPreferences("sharedPref", Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = sharedPref.edit();
        Gson gson = new Gson();
        String json = gson.toJson(input);
        prefsEditor.putString("transmitters", json);
        prefsEditor.apply();
    }
    private FilteredArrayAdapter<CallSign> generateAdapter(CallSign[] callsigns) {
        return new FilteredArrayAdapter<CallSign>(this, R.layout.callsign_layout, callsigns) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {

                    LayoutInflater l = (LayoutInflater) getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                    convertView = l.inflate(R.layout.callsign_layout, parent, false);
                }

                CallSign p = getItem(position);
                ((TextView) convertView.findViewById(R.id.name)).setText(p.getName());
                ((TextView) convertView.findViewById(R.id.token_desc)).setText(p.getDescription());

                return convertView;
            }

            @Override
            protected boolean keepObject(CallSign callsign, String mask) {
                mask = mask.toLowerCase();
                //return callsign.getName().toLowerCase().startsWith(mask) || callsign.getEmail().toLowerCase().startsWith(mask);
                return callsign.getName().toLowerCase().startsWith(mask);
            }
        };
    }


    private void getTransmitterGroups() {
        try {
            ServiceGenerator.changeApiBaseUrl(server);
        } catch (java.lang.NullPointerException e) {
            ServiceGenerator.changeApiBaseUrl("http://www.hampager.de:8080");
        }
        DAPNETAPI service = ServiceGenerator.createService(DAPNETAPI.class, user, password);
        Call<List<TransmitterGroup>> call;
        call = service.getTransmitterGroup("");
        call.enqueue(new Callback<List<TransmitterGroup>>() {
            @Override
            public void onResponse(Call<List<TransmitterGroup>> call, Response<List<TransmitterGroup>> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Connection getting transmittergroups was successful");
                    // tasks available
                    List<TransmitterGroup> data = response.body();
                    Collections.sort(data, new Comparator<TransmitterGroup>() {
                        @Override
                        public int compare(TransmitterGroup o1, TransmitterGroup o2) {
                            int n = o1.getName().compareTo(o2.getName());
                            if (n==0)
                                return o1.getDescription().compareTo(o2.getDescription());
                            else
                                return n;
                        }
                    });
                    TransmitterGroup[] transmitterGroupResources =data.toArray(new TransmitterGroup[data.size()]);
                    saveData(transmitterGroupResources);
                    setTransmittergroups(transmitterGroupResources);
                } else {
                    //APIError error = ErrorUtils.parseError(response);
                    Log.e(TAG, "Error " + response.code());
                    Log.e(TAG, response.message());
                    if (response.code() == 401) {
                        SharedPreferences sharedPref = PostCallActivity.this.getSharedPreferences("sharedPref", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.clear();
                        editor.apply();
                    }
                }
            }

            @Override
            public void onFailure(Call<List<TransmitterGroup>> call, Throwable t) {
                // something went completely wrong (e.g. no internet connection)
                Log.e(TAG, "Error... Do you have internet? "+ t.getMessage());
            }
        });
    }

    private void setTransmittergroups(TransmitterGroup[] data) {
        transmitterGroupCompletion = (TransmitterGroupCompletionView) findViewById(R.id.transmittergroupSearchView);
        transmitterGroupCompletion.setAdapter(generateAdapter(data));
        transmitterGroupCompletion.setTokenListener(new tokenTransmitter());
        transmitterGroupCompletion.setTokenClickStyle(TokenCompleteTextView.TokenClickStyle.Select);
        transmitterGroupCompletion.allowDuplicates(false);
        transmitterGroupCompletion.performBestGuess(true);
        transmitterGroupCompletion.setThreshold(1);
        transmitterGroupCompletion.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_FILTER|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (transmitterGroupCompletion.getObjects()==null||transmitterGroupCompletion.getObjects().size()==0){
            transmitterGroupCompletion.addObject(new TransmitterGroup("ALL"));
        }
    }

    private FilteredArrayAdapter<TransmitterGroup> generateAdapter(TransmitterGroup[] transmittergroups) {
        return new FilteredArrayAdapter<TransmitterGroup>(this, R.layout.callsign_layout, transmittergroups) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {

                    LayoutInflater l = (LayoutInflater) getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                    convertView = l.inflate(R.layout.callsign_layout, parent, false);
                }

                TransmitterGroup p = getItem(position);
                ((TextView) convertView.findViewById(R.id.name)).setText(p.getName());
                ((TextView) convertView.findViewById(R.id.token_desc)).setText(p.getDescription());

                return convertView;
            }

            @Override
            protected boolean keepObject(TransmitterGroup transmittergroup, String mask) {
                mask = mask.toLowerCase();
                //return callsign.getName().toLowerCase().startsWith(mask) || callsign.getEmail().toLowerCase().startsWith(mask);
                return transmittergroup.getName().toLowerCase().startsWith(mask);
            }
        };
    }

    private void sendCall() {
        String msg = message.getText().toString();
        if (callSignsCompletion != null) {
            if (msg.length() != 0 && msg.length() <= 80 && callSignsCompletion.getText().toString().length() != 0) {
                Log.i(TAG, "CSNL,sendcall" + csnl.toString());
                //Forcing completion by focus change to prevent Issue #45
                callSignsCompletion.onFocusChanged(false,View.FOCUS_FORWARD,null);
                transmitterGroupCompletion.onFocusChanged(false,View.FOCUS_FORWARD,null);
                sendCallMethod(msg, csnl, tgnl, emergencyBool, server, user, password);
            } else if (msg.length() == 0)
                genericSnackbar(getString(R.string.error_empty_msg));
            else if (msg.length() > 79)
                genericSnackbar(getString(R.string.error_msg_too_long));
            else if (callSignsCompletion.getText().toString().length() == 0)
                genericSnackbar(getString(R.string.error_empty_callsignlist));
        } else {
            genericSnackbar(getString(R.string.generic_error));
        }
    }

    private void genericSnackbar(String s) {
        Snackbar.make(findViewById(R.id.postcallcoordinator), s, Snackbar.LENGTH_LONG).setAction("Action", null).show();
    }

    private void sendCallMethod(String msg, List<String> csnl, List<String> tgnl, boolean e, String server, String user, String password) {
        CallResource sendvalue = new CallResource(msg, csnl, tgnl, e);
        try {
            ServiceGenerator.changeApiBaseUrl(server);
        } catch (NullPointerException err) {
            ServiceGenerator.changeApiBaseUrl("http://www.hampager.de:8080");
        }
        DAPNETAPI service = ServiceGenerator.createService(DAPNETAPI.class, user, password);
        Call<CallResource> call = service.postCall(sendvalue);
        call.enqueue(new Callback<CallResource>() {
            @Override
            public void onResponse(Call<CallResource> call, Response<CallResource> response) {
                if (response.isSuccessful()) {
                    // tasks available
                    //CallResource returnValue = response.body();
                    Log.i(TAG, "Sending call worked with successful response");
                    Toast.makeText(PostCallActivity.this, getString(R.string.successfully_sent_message), Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    //APIError error = ErrorUtils.parseError(response);
                    Log.e(TAG, "Post Call Error: " + response.code());
                    genericSnackbar("Error:" + response.code() + "Msg: " + response.message());
                    if (response.code() == 401) {
                        SharedPreferences sharedPref = getSharedPreferences("sharedPref", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.clear();
                        editor.apply();
                    }
                }
            }

            @Override
            public void onFailure(Call<CallResource> call, Throwable t) {
                // something went completely south (like no internet connection)
                Log.e(TAG, "Sending call seems to have failed");
                Log.e(TAG, t.toString());
                Snackbar.make(findViewById(R.id.postcallcoordinator), getString(R.string.error_no_internet), Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.sendbutton, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_send) {
            // Check if no view has focus:
            View view = this.getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
            sendCall();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onTokenAdded(CallSign token) {
        csnl.add(token.getName());
    }

    @Override
    public void onTokenRemoved(CallSign token) {
        csnl.remove(token.getName());
    }

    public class tokenTransmitter implements TokenCompleteTextView.TokenListener<TransmitterGroup> {
        @Override
        public void onTokenAdded(TransmitterGroup token) {
            tgnl.add(token.getName());
        }

        @Override
        public void onTokenRemoved(TransmitterGroup token) {
            tgnl.remove(token.getName());
        }
    }

}