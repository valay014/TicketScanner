package com.mirasense.demos;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Time;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.mirasense.scanditsdk.ScanditSDKBarcodePicker;
import com.mirasense.scanditsdk.interfaces.ScanditSDKListener;
import com.mirasense.scanditsdkdemo.R;

public class TicketScanner extends Activity implements ScanditSDKListener {

	// Enter your Scandit SDK App key here. // Your Scandit SDK App key is
	// available via your Scandit SDK web account.
	private static final String sScanditSdkAppKey = "9oqrkm6TEeKQIJOAemIB4q1Pl5WYShcmGTB1btnDwSQ";
	// private static final String serverString =
	// "http://192.168.0.45:8024/WCFService.svc/";
	private static String serverString = "http://tg.cinematix.in/WCFService.svc/";
	// A data structure with O(1) complexity for search and put and read
	private static HashMap<Long, ArrayList<String>> mainHashMap = null;
	// Main Barcode scanning and picking Element
	private ScanditSDKBarcodePicker barcodePicker;
	boolean editclicked = false;
	private TextWatcher whiteSpaceTextwatcher;

	private RelativeLayout scanAreaRLayout, summaryRlayout;
	private View loginFormView, loginStatusView;
	
	private String errorMessageStr, showNameStr, contentViewStr;
	private String userNameStr, passwordStr, showIdStr;
	
	private Button scanButton, numberButton, syncButton;
	private Button summaryButton, closeButton, loginButton;
	
	private TextView summaryTitleTextView, statusTextView;
	private TextView labTotalTicketsToscanTextView,
			labTotalTicketsScannedOnlineTextView,
			labTotalHardTicketsScannedTextView,
			labTotalSoftTicketsScannedTextView;
	private TextView viewTotalTicketsToscanTextView,
			viewTotalTicketsScannedOnlineTextView,
			viewTotalHardTicketsScannedTextView,
			viewTotalSoftTicketsScannedTextView;
	private TextView bottomNotification, loginStatusTextView;

	private ImageView notificationImageView;

	private EditText userNameEditText, passwordEditText;
	private EditText showIdEditText, barcodeText;
	
	// summary scanner View data to show using these values
	private int totalTickets = 0/* Total no of Tickets to scan */,
			scannedTickets = 0/* Total all scanned tickets by all users */,
			hardTicketScanned = 0/* Total Tickets scanned by this user */,
			softTicketScanned = 0;/* Tickets scanned by this user session */
	
	// Background activity task management
	private AsyncConnectionToWebservice logInTask = null;
	private AsyncConnectionToWebservice syncTask = null;
	private AsyncConnectionToWebservice logoutTask = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		whiteSpaceTextwatcher = new TextWatcher() {
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				editclicked = true;
			}
			@Override
			public void afterTextChanged(Editable s) {
				editclicked = false;
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
		};
		// Create all the View On application Startup ....
		// So in between scanning is fast enough ...
		createScanAreaView(); // Scan Area with scandit barcode Picker
		createSummaryView(); // Show summary of show and end of show
		createLoginView(); // Login, Sync and Logout view
		
	}

	@Override
	protected void onPause() {
		// When the activity is in the background immediately stop the
		// scanning to save resources and free the camera.
		if (barcodePicker != null) {
			barcodePicker.stopScanning();
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		// Once the activity is in the foreground again, restart scanning.
		if (barcodePicker != null) {
			barcodePicker.startScanning();
		}
		super.onResume();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (editclicked) {
			if (event.getAction() == KeyEvent.KEYCODE_SPACE)
				return false;
			super.dispatchKeyEvent(event);
		} else if (event.getKeyCode() == 66) {
			try {
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				if (contentViewStr.equals("loginView")
						&& loginButton.getText().toString().equals("Sign In")) {
					imm.hideSoftInputFromWindow(
							showIdEditText.getApplicationWindowToken(), 0);
					attemptConnection("LoginTask");
				} else if ((contentViewStr.equals("loginView") && loginButton
						.getText().toString().equals("Log Out"))) {
					imm.hideSoftInputFromWindow(
							passwordEditText.getApplicationWindowToken(), 0);
					attemptConnection("LogoutTask");
				} else if (contentViewStr.equals("loginView")
						&& loginButton.getText().toString().equals("Sync")) {
					imm.hideSoftInputFromWindow(
							passwordEditText.getApplicationWindowToken(), 0);
					attemptConnection("SyncTask");
				} else if (contentViewStr.equals("rootView")
						&& numberButton.getText().toString().equals("Reset")) {
					imm.hideSoftInputFromWindow(
							barcodeText.getApplicationWindowToken(), 0);
					verifyCall();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return super.dispatchKeyEvent(event);
	}
	
	private void createScanAreaView() {
		// creating main view that hold scandit scanner and other operatives
		scanAreaRLayout = new RelativeLayout(this);
		scanAreaRLayout.setBackgroundResource(R.drawable.background);
		scanAreaRLayout.setFocusable(true);
		// for getting screen size
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		// for Satus text View
		statusTextView = new TextView(this);
		statusTextView.setText("Ready to Scan");
		statusTextView.setTextColor(Color.GREEN);
		statusTextView.setTextSize(20);
		statusTextView.setGravity(Gravity.CENTER);

		RelativeLayout.LayoutParams statusTextViewParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		statusTextViewParams.width = display.getWidth();
		statusTextViewParams.topMargin = 0;
		statusTextViewParams.height = display.getHeight() / 14;
		scanAreaRLayout.addView(statusTextView, statusTextViewParams);

		// start scanning screen.
		RelativeLayout.LayoutParams barcodePickerParam;
		barcodePicker = new ScanditSDKBarcodePicker(TicketScanner.this,
				sScanditSdkAppKey);
		barcodePicker.getOverlayView().addListener(TicketScanner.this);
		barcodePickerParam = new RelativeLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		barcodePickerParam.addRule(RelativeLayout.CENTER_HORIZONTAL);
		barcodePickerParam.topMargin = statusTextViewParams.height;
		barcodePickerParam.width = display.getWidth();
		barcodePickerParam.height = (display.getHeight() / 2);
		scanAreaRLayout.addView(barcodePicker, barcodePickerParam);

		// NotificationImageView
		notificationImageView = new ImageView(this);
		notificationImageView.setVisibility(View.GONE);
		scanAreaRLayout.addView(notificationImageView, barcodePickerParam);

		// ///////////////////////////////////////////////////////
		barcodeText = new EditText(this);
		barcodeText.setText("");
		barcodeText.setSingleLine();
		barcodeText.setInputType(InputType.TYPE_CLASS_NUMBER);
		barcodeText.setEnabled(false);

		// Avoid whitespace to be inserted inside the EditText
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput((barcodeText), InputMethodManager.SHOW_IMPLICIT);
		barcodeText.addTextChangedListener(whiteSpaceTextwatcher);

		// Set position of ui items in layouts
		RelativeLayout.LayoutParams barcodeTextParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

		barcodeTextParams.topMargin = barcodePickerParam.height
				+ barcodePickerParam.topMargin;
		barcodeTextParams.rightMargin = 20;
		barcodeTextParams.leftMargin = 20;
		barcodeTextParams.width = display.getWidth();
		// ///////////////////////////////////////////////////////

		scanButton = new Button(this);
		RelativeLayout.LayoutParams scanButtonParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		scanButtonParams.topMargin = barcodeTextParams.topMargin
				+ (display.getWidth() / 7);
		scanButtonParams.width = display.getWidth() / 2 - 10;
		scanButtonParams.height = scanButtonParams.width / 3;
		scanButtonParams.leftMargin = 5;
		// ///////////////////////////////////////////////////////
		numberButton = new Button(this);
		numberButton.setText("Enter Number");
		RelativeLayout.LayoutParams numberButtonParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		numberButtonParams.topMargin = scanButtonParams.topMargin;
		numberButtonParams.width = display.getWidth() / 2 - 10;
		numberButtonParams.height = scanButtonParams.width / 3;
		numberButtonParams.leftMargin = display.getWidth() / 2 + 5;
		// ///////////////////////////////////////////////////////
		syncButton = new Button(this);
		syncButton.setText("Sync");
		RelativeLayout.LayoutParams syncButtonParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		syncButtonParams.topMargin = scanButtonParams.topMargin
				+ (display.getWidth() / 6);
		syncButtonParams.width = display.getWidth() / 2 - 10;
		syncButtonParams.height = scanButtonParams.width / 3;
		syncButtonParams.leftMargin = display.getWidth() / 2 + 5;
		syncButton.setVisibility(View.GONE);
		// ///////////////////////////////////////////////////////
		summaryButton = new Button(this);
		summaryButton.setText("Summary");
		RelativeLayout.LayoutParams summaryButtonParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		summaryButtonParams.topMargin = syncButtonParams.topMargin;
		summaryButtonParams.width = display.getWidth() / 2 - 10;
		summaryButtonParams.height = scanButtonParams.width / 3;
		summaryButtonParams.leftMargin = 5;
		summaryButton.setVisibility(View.GONE);

		scanAreaRLayout.addView(barcodeText, barcodeTextParams);
		scanAreaRLayout.addView(scanButton, scanButtonParams);
		scanAreaRLayout.addView(numberButton, numberButtonParams);
		scanAreaRLayout.addView(syncButton, syncButtonParams);
		scanAreaRLayout.addView(summaryButton, summaryButtonParams);
		scanButton.setText("Verify");

		syncButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// whatever should be done when answering "YES" goes here
				if (barcodePicker != null) {
					barcodePicker.stopScanning();
				}
				setContentView(R.layout.activity_login);
				contentViewStr = "loginView";
				loginButton = (Button) findViewById(R.id.sign_in_button);
				loginButton.setText("Sync");
				showIdEditText = (EditText) findViewById(R.id.showid);
				userNameEditText = (EditText) findViewById(R.id.email);
				showIdEditText.setText(showIdStr);
				showIdEditText.setEnabled(false);
				userNameEditText.setText(userNameStr);
				userNameEditText.setEnabled(false);
				passwordEditText = (EditText) findViewById(R.id.password);
				passwordEditText.requestFocus();

				loginButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if (loginButton.getText().toString().equals("Sync")) {
							// sync scanned data with web service;
							try {
								attemptConnection("SyncTask");
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

					}
				});

			}
		});

		numberButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				barcodeText.setFocusableInTouchMode(true);
				barcodeText.setFocusable(true);
				if (numberButton.getText().equals("Enter Number")) {
					barcodePicker.setVisibility(View.GONE);
					notificationImageView.setImageResource(R.drawable.enternum);
					notificationImageView.setVisibility(View.VISIBLE);
					barcodeText.setEnabled(true);
					barcodeText.setText("");
					numberButton.setText("Reset");
					scanButton.setText("Verify");
					statusTextView.setText("Enter Ticket Number");
					statusTextView.setTextColor(Color.GREEN);
				} else if (numberButton.getText().equals("Reset"))
					barcodeText.setText("");
			}
		});

		summaryButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				setContentView(summaryRlayout);
				contentViewStr = "summaryView";
				viewTotalTicketsToscanTextView.setText("" + totalTickets);
				viewTotalTicketsScannedOnlineTextView.setText(""
						+ scannedTickets);
				viewTotalHardTicketsScannedTextView.setText(""
						+ hardTicketScanned);
				viewTotalSoftTicketsScannedTextView.setText(""
						+ softTicketScanned);
				summaryTitleTextView.setText("Summary Of" + "\n"
						+ getShowName());

			}
		});

		scanButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (scanButton.getText().equals("Scan Ticket")) {
					// mBarcodePicker.startScanning();
					barcodePicker.setVisibility(View.VISIBLE);
					barcodePicker.requestFocus();
					notificationImageView.setVisibility(View.GONE);
					barcodeText.setText("");
					statusTextView.setText("Ready to Scan");
					statusTextView.setTextColor(Color.GREEN);
					scanButton.setText("Verify");
				} else if (scanButton.getText().equals("Verify")){
					verifyCall();
				}
			}
		});
		// Must be able to run the portrait version for this button to work.
		if (!ScanditSDKBarcodePicker.canRunPortraitPicker()) {
			scanButton.setEnabled(false);
		}
	}

	public void verifyCall() {
		barcodeText.setEnabled(false);
		barcodeText.setFocusable(false);
		if (TextUtils.isEmpty(barcodeText.getText().toString())) {
			// barcodeText.requestFocus();
			barcodeText
					.setError(getString(R.string.error_field_required));
			if (numberButton.getText().toString().equals("Reset")) {
				barcodeText.setFocusableInTouchMode(true);
				barcodeText.setFocusable(true);
				barcodeText.requestFocus();
				barcodeText.setEnabled(true);
			}
		} else {
			// scanButton.requestFocus();
			scanButton.setText("Scan Ticket");
			numberButton.setText("Enter Number");
			barcodePicker.setVisibility(View.GONE);

			// get data for the scanned barcode number
			ArrayList<String> resultList = mainHashMap.get(Long
					.valueOf(barcodeText.getText().toString()));

			if (resultList == null) {
				notificationImageView
						.setImageResource(R.drawable.invalid);
				statusTextView.setText("Invalid Ticket number");
				statusTextView.setTextColor(Color.RED);
				statusTextView.setFocusable(true);
			} else if (resultList.get(1).equalsIgnoreCase("false")) {
				notificationImageView
						.setImageResource(R.drawable.valid);
				Time today = new Time(Time.getCurrentTimezone());
				today.setToNow();
				String ticketTime = today.format("%k:%M:%S");
				String scannedTime = "Scanned Time:" + ticketTime;
				// update scanning data in the data structure
				resultList.set(1, "true");
				resultList.set(2, ticketTime);
				resultList.set(3, userNameStr);
				statusTextView.setText(scannedTime);
				statusTextView.setTextColor(Color.GREEN);
				softTicketScanned++;
				hardTicketScanned++;
				// Add scanned tickets to local file for sync
				addToFile(resultList);
			} else if (resultList.get(1).equalsIgnoreCase("true")) {
				notificationImageView
						.setImageResource(R.drawable.invalid);
				statusTextView.setText("Used ticket"
						+ resultList.get(2));
				statusTextView.setTextColor(Color.RED);
			}
			notificationImageView.setVisibility(View.VISIBLE);
		}
	}
	
	public void createSummaryView() {

		summaryRlayout = new RelativeLayout(this);
		summaryRlayout.setBackgroundResource(R.drawable.background);

		// for getting screen size
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();

		// Setup summary form
		summaryTitleTextView = new TextView(this);
		summaryTitleTextView.setLines(2);
		summaryTitleTextView.setTextSize(18);
		summaryTitleTextView.setText("Summary Of" + "\n" + getShowName());
		summaryTitleTextView.setTextColor(Color.WHITE);
		RelativeLayout.LayoutParams summaryStatusTitleParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		summaryStatusTitleParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
		// float x=display.getHeight();
		summaryStatusTitleParams.topMargin = (display.getHeight() / 16);
		summaryRlayout.addView(summaryTitleTextView, summaryStatusTitleParams);

		labTotalTicketsToscanTextView = new TextView(this);
		labTotalTicketsToscanTextView.setText("Total Tickets To Scan:");
		labTotalTicketsToscanTextView.setTextColor(Color.MAGENTA);
		labTotalTicketsToscanTextView.setTextSize(16);
		RelativeLayout.LayoutParams labTotalTicketsToscanParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		labTotalTicketsToscanParams.topMargin = summaryStatusTitleParams.topMargin
				+ (display.getWidth() / 4);
		labTotalTicketsToscanParams.leftMargin = 20;
		// labTotalTicketsToscanParams.addRule(RelativeLayout.LEFT_OF);
		summaryRlayout.addView(labTotalTicketsToscanTextView,
				labTotalTicketsToscanParams);

		viewTotalTicketsToscanTextView = new TextView(this);
		viewTotalTicketsToscanTextView.setText("100");
		viewTotalTicketsToscanTextView.setTextSize(16);
		viewTotalTicketsToscanTextView.setTextColor(Color.MAGENTA);
		RelativeLayout.LayoutParams viewTotalTicketsToscanParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		viewTotalTicketsToscanParams.topMargin = labTotalTicketsToscanParams.topMargin;
		// viewTotalTicketsToscanParams.addRule(RelativeLayout.RIGHT_OF);
		viewTotalTicketsToscanParams.leftMargin = display.getWidth() / 2
				+ display.getWidth() / 4;
		// viewTotalTicketsToscanParams.rightMargin=20;
		summaryRlayout.addView(viewTotalTicketsToscanTextView,
				viewTotalTicketsToscanParams);

		labTotalTicketsScannedOnlineTextView = new TextView(this);
		labTotalTicketsScannedOnlineTextView.setText("Total Tickets Scanned:");
		labTotalTicketsScannedOnlineTextView.setTextSize(16);
		labTotalTicketsScannedOnlineTextView.setTextColor(Color.CYAN);
		RelativeLayout.LayoutParams labTotalTicketsScannedOnlineParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		labTotalTicketsScannedOnlineParams.topMargin = labTotalTicketsToscanParams.topMargin
				+ display.getWidth() / 7;
		labTotalTicketsScannedOnlineParams.leftMargin = 20;
		// labTotalTicketsToscanParams.addRule(RelativeLayout.LEFT_OF);
		summaryRlayout.addView(labTotalTicketsScannedOnlineTextView,
				labTotalTicketsScannedOnlineParams);

		viewTotalTicketsScannedOnlineTextView = new TextView(this);
		viewTotalTicketsScannedOnlineTextView.setText("50");
		viewTotalTicketsScannedOnlineTextView.setTextSize(16);
		viewTotalTicketsScannedOnlineTextView.setTextColor(Color.CYAN);
		RelativeLayout.LayoutParams viewTotalTicketsScannedOnlineParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		viewTotalTicketsScannedOnlineParams.topMargin = labTotalTicketsScannedOnlineParams.topMargin;
		viewTotalTicketsScannedOnlineParams.leftMargin = display.getWidth() / 2
				+ display.getWidth() / 4;
		// labTotalTicketsToscanParams.addRule(RelativeLayout.LEFT_OF);
		summaryRlayout.addView(viewTotalTicketsScannedOnlineTextView,
				viewTotalTicketsScannedOnlineParams);

		labTotalHardTicketsScannedTextView = new TextView(this);
		labTotalHardTicketsScannedTextView.setText("Your Scanned Tickets:");
		labTotalHardTicketsScannedTextView.setTextSize(16);
		labTotalHardTicketsScannedTextView.setTextColor(Color.MAGENTA);
		RelativeLayout.LayoutParams labTotalHardTicketsScannedParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		labTotalHardTicketsScannedParams.topMargin = labTotalTicketsScannedOnlineParams.topMargin
				+ display.getWidth() / 7;
		labTotalHardTicketsScannedParams.leftMargin = 20;
		// labTotalTicketsToscanParams.addRule(RelativeLayout.LEFT_OF);
		summaryRlayout.addView(labTotalHardTicketsScannedTextView,
				labTotalHardTicketsScannedParams);

		viewTotalHardTicketsScannedTextView = new TextView(this);
		viewTotalHardTicketsScannedTextView.setText("45");
		viewTotalHardTicketsScannedTextView.setTextSize(16);
		viewTotalHardTicketsScannedTextView.setTextColor(Color.MAGENTA);
		RelativeLayout.LayoutParams viewTotalHardTicketsScannedParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		viewTotalHardTicketsScannedParams.topMargin = labTotalHardTicketsScannedParams.topMargin;
		viewTotalHardTicketsScannedParams.leftMargin = display.getWidth() / 2
				+ display.getWidth() / 4;
		// labTotalTicketsToscanParams.addRule(RelativeLayout.LEFT_OF);
		summaryRlayout.addView(viewTotalHardTicketsScannedTextView,
				viewTotalHardTicketsScannedParams);

		labTotalSoftTicketsScannedTextView = new TextView(this);
		labTotalSoftTicketsScannedTextView
				.setText("Scanned Tickets To Upload:");
		labTotalSoftTicketsScannedTextView.setTextSize(16);
		labTotalSoftTicketsScannedTextView.setTextColor(Color.CYAN);
		RelativeLayout.LayoutParams labTotalSoftTicketsScannedParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		labTotalSoftTicketsScannedParams.topMargin = labTotalHardTicketsScannedParams.topMargin
				+ display.getWidth() / 7;
		labTotalSoftTicketsScannedParams.leftMargin = 20;
		// labTotalTicketsToscanParams.addRule(RelativeLayout.LEFT_OF);
		summaryRlayout.addView(labTotalSoftTicketsScannedTextView,
				labTotalSoftTicketsScannedParams);

		viewTotalSoftTicketsScannedTextView = new TextView(this);
		viewTotalSoftTicketsScannedTextView.setText("45");
		viewTotalSoftTicketsScannedTextView.setTextSize(16);
		viewTotalSoftTicketsScannedTextView.setTextColor(Color.CYAN);
		RelativeLayout.LayoutParams viewTotalSoftTicketsScannedParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		viewTotalSoftTicketsScannedParams.topMargin = labTotalSoftTicketsScannedParams.topMargin;
		viewTotalSoftTicketsScannedParams.leftMargin = display.getWidth() / 2
				+ display.getWidth() / 4;
		// labTotalTicketsToscanParams.addRule(RelativeLayout.LEFT_OF);
		summaryRlayout.addView(viewTotalSoftTicketsScannedTextView,
				viewTotalSoftTicketsScannedParams);

		closeButton = new Button(this);
		closeButton.setText("Back");
		closeButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				if (closeButton.getText().toString().equals("Back")) {
					setContentView(scanAreaRLayout);
					contentViewStr = "rootView";
				} else {
					finish();
				}
			}
		});

		RelativeLayout.LayoutParams buttonCloseParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		buttonCloseParams.topMargin = viewTotalSoftTicketsScannedParams.topMargin
				+ display.getWidth() / 7;
		buttonCloseParams.leftMargin = 20;
		buttonCloseParams.width = display.getWidth() - 40;
		// labTotalTicketsToscanParams.addRule(RelativeLayout.LEFT_OF);
		summaryRlayout.addView(closeButton, buttonCloseParams);

		bottomNotification = new TextView(this);
		bottomNotification.setLines(2);
		bottomNotification.setText("");
		RelativeLayout.LayoutParams bottomNotificationParams = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		bottomNotificationParams.topMargin = buttonCloseParams.topMargin
				+ display.getWidth() / 6;
		// viewTotalHardTicketsScannedParams.leftMargin=display.getWidth()/2+display.getWidth()/4;
		bottomNotificationParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
		summaryRlayout.addView(bottomNotification, bottomNotificationParams);

	}

	public void createLoginView() {
		setContentView(R.layout.activity_login);
		contentViewStr = "loginView";

		// Set up the login form.
		showIdEditText = (EditText) findViewById(R.id.showid);
		showIdEditText.addTextChangedListener(whiteSpaceTextwatcher);

		userNameEditText = (EditText) findViewById(R.id.email);
		userNameEditText.addTextChangedListener(whiteSpaceTextwatcher);

		passwordEditText = (EditText) findViewById(R.id.password);
		passwordEditText.addTextChangedListener(whiteSpaceTextwatcher);
		passwordEditText
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView textView, int id,
							KeyEvent keyEvent) {
						if (id == R.id.login || id == EditorInfo.IME_NULL) {
							try {
								attemptConnection("LoginTask");
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							return true;
						}
						return false;
					}
				});

		loginFormView = findViewById(R.id.login_form);
		loginStatusView = findViewById(R.id.login_status);
		loginStatusTextView = (TextView) findViewById(R.id.login_status_message);
		loginButton = (Button) findViewById(R.id.sign_in_button);

		loginButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (loginButton.getText().toString().equals("Sign In")) {
					try {
						attemptConnection("LoginTask");
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					finish();
				}
			}
		});
	}

	// Android Alert Dialog box for showing exit, logout, and server setting
	public AlertDialog makeAndShowDialogBox(String dialogType) {
		AlertDialog alertDialogBox;
		if (dialogType.equals("logoutDialog")) {
			alertDialogBox = new AlertDialog.Builder(this)
					// set message, title, and icon
					.setTitle("Terminator")
					.setMessage("Are you sure that you want to Log Out?")
					.setIcon(R.drawable.logout)
					// set three option buttons
					.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									if (barcodePicker != null) {
										barcodePicker.stopScanning();
									}
									setContentView(R.layout.activity_login);
									contentViewStr = "loginView";
									loginButton = (Button) findViewById(R.id.sign_in_button);
									loginButton.setText("Log Out");
									showIdEditText = (EditText) findViewById(R.id.showid);
									userNameEditText = (EditText) findViewById(R.id.email);

									showIdEditText.setText(showIdStr);
									showIdEditText.setEnabled(false);
									userNameEditText.setText(userNameStr);
									userNameEditText.setEnabled(false);
									passwordEditText = (EditText) findViewById(R.id.password);
									passwordEditText
											.addTextChangedListener(whiteSpaceTextwatcher);
									passwordEditText.requestFocus();
									loginButton
											.setOnClickListener(new View.OnClickListener() {
												@Override
												public void onClick(View view) {
													if (loginButton.getText()
															.toString()
															.equals("Sign In")) {
														try {
															attemptConnection("LoginTask");
														} catch (IOException e) {
															e.printStackTrace();
														}
													} else {
														try {
															attemptConnection("LogoutTask");
														} catch (IOException e) {
															e.printStackTrace();
														}
													}
												}
											});
								}
							})

					.setNegativeButton("NO",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									
								}
							})// setNegativeButton

					.create();

			return alertDialogBox;
		} else if (dialogType.equals("exitDialog")) {
			alertDialogBox = new AlertDialog.Builder(this)
					.setTitle("Terminator")
					.setMessage("Are you sure that you want to Exit?")
					.setIcon(R.drawable.logout)
					.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									finish();
								}
							})// setNegativeButton
					.setNegativeButton("NO",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
								}
							})// setNegativeButton
					.create();
			return alertDialogBox;
		} else {
			final EditText settingView = new EditText(this);
			settingView.setHint("e.g:www.hostName.com");
			alertDialogBox = new AlertDialog.Builder(this)
					.setView(settingView)
					.setTitle("Server Setting")
					.setMessage("Enter Connection String")
					.setIcon(R.drawable.logout)
					.setPositiveButton("Save",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									if (settingView.getText().toString()
											.endsWith("/")) {
										serverString = "http://"
												+ settingView.getText()
														.toString()
												+ "WCFService.svc/";
									} else {
										serverString = "http://"
												+ settingView.getText()
														.toString()
												+ "/WCFService.svc/";
									}

								}
							})// setNegativeButton

					.setNegativeButton("Cancel",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
								}
							})// setNegativeButton
					.create();
			return alertDialogBox;
		}
	}

	/**
	 * Called when a barcode has been decoded successfully.
	 * 
	 * @param barcode
	 *            -Scanned barcode content.
	 * @param symbology
	 *            -Scanned barcode symbology.
	 */
	public void didScanBarcode(String barcode, String symbology) {
		// Example code that would typically be used in a real-world app using
		// the Scandit SDK.
		/*
		 * // Access the image in which the bar code has been recognized. byte[]
		 * imageDataNV21Encoded =
		 * barcodePicker.getCameraPreviewImageOfFirstBarcodeRecognition(); int
		 * imageWidth = barcodePicker.getCameraPreviewImageWidth(); int
		 * imageHeight = barcodePicker.getCameraPreviewImageHeight();
		 */
		
		barcodeText.setText(barcode);
		verifyCall();
		scanButton.setText("Scan Ticket");
	//	scanButton.setText("Verify");
		barcodeText.setError(null);
	}

	/**
	 * Called when the user entered a bar code manually.
	 * 
	 * @param entry
	 *            The information entered by the user.
	 */
	public void didManualSearch(String entry) {
		// Example code that would typically be used in a real-world app usin

		// the Scandit SDK.
		/*
		 * // Access the current camera image. byte[] imageDataNV21Encoded =
		 * barcodePicker.getMostRecentCameraPreviewImage(); int imageWidth =
		 * barcodePicker.getCameraPreviewImageWidth(); int imageHeight =
		 * barcodePicker.getCameraPreviewImageHeight();
		 * 
		 * // Stop recognition to save resources. mBarcodePicker.stopScanning();
		 */
	}

	@Override
	public void didCancel() {
	}

	@Override
	public void onBackPressed() {

		if (contentViewStr.equals("summaryView")
				&& closeButton.getText().toString().equals("Back")) {
			setContentView(scanAreaRLayout);
			contentViewStr = "rootView";
		}

		else if ((numberButton.getText().toString().equals("Reset") && contentViewStr
				.equals("rootView"))) {

			barcodePicker.startScanning();
			barcodePicker.setVisibility(View.VISIBLE);
			barcodePicker.requestFocus();
			notificationImageView.setVisibility(View.GONE);

			statusTextView.setText("Ready to Scan");
			statusTextView.setTextColor(Color.GREEN);
			scanButton.setText("Verify");
			setContentView(scanAreaRLayout);
			contentViewStr = "rootView";
			barcodeText.setText("");
			barcodeText.setFocusable(false);
			barcodeText.setEnabled(false);
			numberButton.setText("Enter Number");
		} else if (contentViewStr.equals("loginView")
				&& loginButton.getText().toString().equals("Sync")) {
			if (!numberButton.getText().equals("Reset")) {
				barcodePicker.startScanning();
				barcodePicker.setVisibility(View.VISIBLE);
				barcodePicker.requestFocus();
				notificationImageView.setVisibility(View.GONE);
			}
			statusTextView.setText("Ready to Scan");
			statusTextView.setTextColor(Color.GREEN);
			scanButton.setText("Verify");
			setContentView(scanAreaRLayout);
			contentViewStr = "rootView";
		} else if (contentViewStr.equals("loginView")
				&& loginButton.getText().toString().equals("Sign In")) {
			finish();
		} else if ((contentViewStr.equals("loginView") && loginButton.getText()
				.toString().equals("Log Out"))
				|| (contentViewStr.equals("summaryView") && closeButton
						.getText().toString().equals("Exit"))) {

		} else {
			AlertDialog diaBox = makeAndShowDialogBox("logoutDialog");
			diaBox.show();
		}
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if ((contentViewStr.equals("loginView") && loginButton.getText()
				.toString().equals("Sign In"))) {
			menu.add(1, 2, 2, "Exit");
			menu.add(1, 3, 3, "Setting");
		} else if ((contentViewStr.equals("summaryView") && closeButton
				.getText().toString().equals("Exit"))
				|| (contentViewStr.equals("loginView") && loginButton.getText()
						.toString().equals("Log Out"))) {
			menu.add(1, 2, 2, "Exit");
		} else {
			menu.add(1, 1, 1, "Log Out");
			menu.add(1, 2, 2, "Exit");
		}
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(1, 2, 2, "Exit");
		menu.add(1, 3, 3, "Setting");
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case 1:
			AlertDialog logoutdiaBox = makeAndShowDialogBox("logoutDialog");
			logoutdiaBox.show();
			// Write your log out things
			return true;
		case 2:
			AlertDialog exitdiaBox = makeAndShowDialogBox("exitDialog");
			exitdiaBox.show();
			return true;
		case 3:
			AlertDialog settingdiaBox = makeAndShowDialogBox("settingDialog");
			settingdiaBox.show();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
	}

	public String ticketAuthentication(String ticketNo) {
		return ticketNo;
	}

	/**
	 * method will be used to get data and send data to web service loginUrl :
	 * "http://webaddress/Servicename/username/password/showID"; syncUrl :
	 * "http://webaddress/Servicename/username/password/showID/?xml=xmlData";
	 * logOutYrl :
	 * "http://webaddress/Servicename/username/password/showID/?xml=xmlData";
	 * 
	 * @param
	 * @return
	 */

	public String connectToWebService(URL url) {
		String xmlData = null;
		HttpURLConnection conn = null;
		// Get the response
		BufferedReader br = null;
		try {
			conn = (HttpURLConnection) url.openConnection();
			br = new BufferedReader(
					new InputStreamReader(conn.getInputStream()));
			xmlData = br.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// return XML data in string
		return xmlData;
	}

	/**
	 * This method will convert xmlString which we get from webservice to xml
	 * Document which will be used to parse data and put into Internal data
	 * structure by toDataStructure(Document xmlDoc)
	 * 
	 * @param xmlList
	 * @return
	 */
	public boolean addToFile(ArrayList<String> xmlList) {
		FileOutputStream outputStream = null;
		try {
			outputStream = openFileOutput(showIdStr + ".tsc",
					Context.MODE_APPEND);
			String tempStr = "<Ticket " + "TicketNo=\"" + xmlList.get(0) + "\""
					+ " Status=\"" + xmlList.get(1) + "\"" + " ScannedTime=\""
					+ xmlList.get(2) + "\"" + " User=\"" + xmlList.get(3)
					+ "\"" + "/>";
			outputStream.write(tempStr.getBytes());
			outputStream.close();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	// ///////////// Some XML helping methods //
	// ////////////////////////////////////
	/**
	 * This method will convert xmlString which we get from webservice to xml
	 * Document which will be used to parse data and put into Internal data
	 * structure by toDataStructure(Document xmlDoc)
	 * 
	 * @param xmlString
	 * @return
	 */
	public Document toXML(String xmlString) {
		// current parameter is String but later we will replace it with some
		// data structure
		Document xmlDoc = null;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			InputSource is = new InputSource();
			is.setCharacterStream(new StringReader(xmlString));
			xmlDoc = db.parse(is);
		} catch (ParserConfigurationException e) {
			Log.e("Error: ", e.getMessage());
			return null;
		} catch (SAXException e) {
			Log.e("Error: ", e.getMessage());
			return null;
		} catch (IOException e) {
			Log.e("Error: ", e.getMessage());
			return null;
		}
		// return DOM
		return xmlDoc;
	}

	/**
	 * This method will convert xml Document to internal Data Structure which
	 * will be used to scan data inside
	 * 
	 * @param xmlDoc
	 *            -Document of xml that from the web service
	 * @return boolean-True if get successful data otherwise false
	 */

	public boolean toDataStructure(Document xmlDoc) {
		// current parameter is String but later we will replace it with some
		// data structure
		setErrorMessageStr(null);
		// Best data structure we should use that help fast searching with O(1)
		if (mainHashMap == null)
			mainHashMap = new HashMap<Long, ArrayList<String>>();
		else
			mainHashMap.clear();
		NodeList tempNodeList = null;
		NamedNodeMap tempNamedNodeMap = null;
		xmlDoc.getDocumentElement().normalize();
		// For Authentication NodeF
		tempNodeList = xmlDoc.getElementsByTagName("Authentication");
		tempNamedNodeMap = tempNodeList.item(0).getAttributes();
		if (!tempNamedNodeMap.getNamedItem("Status").getNodeValue()
				.equalsIgnoreCase("true")) {
			setErrorMessageStr(tempNamedNodeMap.getNamedItem("Reason")
					.getNodeValue());
			return false;
		}
		// For Show Node
		tempNodeList = xmlDoc.getElementsByTagName("Show");
		String temp = xmlDoc.getNodeValue();
		tempNamedNodeMap = tempNodeList.item(0).getAttributes();
		if (!tempNamedNodeMap.getNamedItem("Status").getNodeValue()
				.equalsIgnoreCase("true")) {
			setErrorMessageStr(tempNamedNodeMap.getNamedItem("Reason")
					.getNodeValue());
			return false;
		} else {
			setShowName(tempNamedNodeMap.getNamedItem("ShowName")
					.getNodeValue());
			Element eElement = (Element) tempNodeList.item(0);
			if (eElement.getTextContent().length() != 0) {
				scannedTickets = Integer.parseInt(eElement.getTextContent());
				return true;
			}
		}
		// For Show's Ticket Nodes
		tempNodeList = xmlDoc.getElementsByTagName("Ticket");
		totalTickets = 0;
		scannedTickets = 0;
		hardTicketScanned = 0;
		totalTickets = tempNodeList.getLength();
		// process all the ticket data and get it into data structure...
		// with key as ticket number
		for (int i = 0; i < tempNodeList.getLength(); i++) {
			NamedNodeMap ticketAttr = tempNodeList.item(i).getAttributes();
			ArrayList<String> tempList = new ArrayList<String>();
			tempList.add(ticketAttr.getNamedItem("TicketNo").getNodeValue());
			tempList.add(ticketAttr.getNamedItem("Status").getNodeValue());

			tempList.add(ticketAttr.getNamedItem("ScannedTime").getNodeValue());
			tempList.add(ticketAttr.getNamedItem("User").getNodeValue());
			if (ticketAttr.getNamedItem("Status").getNodeValue()
					.equalsIgnoreCase("true")) {
				if (ticketAttr.getNamedItem("User").getNodeValue()
						.equalsIgnoreCase(userNameStr)) {
					hardTicketScanned++;
				}
				scannedTickets++;
			}
			// Add data inside the data structure which can be while scanning
			mainHashMap.put(Long.valueOf(tempList.get(0)), tempList);
		}
		return true;
	}

	public String getErrorMessageStr() {
		return errorMessageStr;
	}

	public void setErrorMessageStr(String messageStr) {
		this.errorMessageStr = messageStr;
	}

	public String getShowName() {
		return showNameStr;
	}

	public void setShowName(String showName) {
		this.showNameStr = showName;
	}

	/**
	 * Attempts to sign in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 * 
	 * @throws IOException
	 * @param taskType
	 */

	public void attemptConnection(String taskType) throws IOException {
		if (taskType.equals("LoginTask")) {
			if (logInTask != null)
				return;
		} else if (taskType.equals("SyncTask")) {
			if (syncTask != null)
				return;
		} else if(taskType.equals("LogoutTask")){
			if (logoutTask != null)
				return;
		}
		passwordEditText = (EditText) findViewById(R.id.password);
		passwordEditText.addTextChangedListener(whiteSpaceTextwatcher);
		// Reset errors.
		userNameEditText.setError(null);
		passwordEditText.setError(null);
		showIdEditText.setError(null);
		// Store values at the time of the login attempt.
		passwordStr = passwordEditText.getText().toString();
		userNameStr = userNameEditText.getText().toString();
		showIdStr = showIdEditText.getText().toString();
		boolean cancel = false;
		View focusView = null;
		// Check for a valid password.
		if (TextUtils.isEmpty(passwordStr)) {
			passwordEditText.setError(getString(R.string.error_field_required));
			focusView = passwordEditText;
			cancel = true;
		}
		// Check for a valid user name.
		if (TextUtils.isEmpty(userNameStr)) {
			userNameEditText.setError(getString(R.string.error_field_required));
			cancel = true;
		}
		// Check for a valid Show ID.
		if (TextUtils.isEmpty(showIdStr)) {
			showIdEditText.setError(getString(R.string.error_field_required));
			focusView = showIdEditText;
			cancel = true;
		}
		if (cancel) {
			focusView.requestFocus();
		} else {
			// Show a progress spinner, and kick off a background task to
			loginStatusTextView = (TextView) findViewById(R.id.login_status_message);	
			if (taskType.equals("LoginTask")) {
				loginStatusTextView.setText("Signing In");
				showProgress(true);
				logInTask = new AsyncConnectionToWebservice("LoginTask");
				logInTask.execute((Void) null);
			} else if (taskType.equals("SyncTask")) {
				loginStatusTextView.setText("Synchronizing");
				showProgress(true);
				syncTask = new AsyncConnectionToWebservice("SyncTask");
				syncTask.execute((Void) null);
			} else if (taskType.equals("LogoutTask")) {
				loginStatusTextView.setText("Loging Out");
				showProgress(true);
				logoutTask = new AsyncConnectionToWebservice("LogoutTask");
				logoutTask.execute((Void) null);
			}
		}
	}

	/**
	 * Shows the progress UI and hides the login form.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	private void showProgress(final boolean show) {
		// the progress spinner.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			int shortAnimTime = getResources().getInteger(
					android.R.integer.config_shortAnimTime);

			loginFormView = findViewById(R.id.login_form);
			loginStatusView = findViewById(R.id.login_status);
			loginStatusView.setVisibility(View.VISIBLE);
			loginStatusView.animate().setDuration(shortAnimTime)
					.alpha(show ? 1 : 0)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							loginStatusView.setVisibility(show ? View.VISIBLE
									: View.GONE);
						}
					});
			loginFormView.setVisibility(View.VISIBLE);
			loginFormView.animate().setDuration(shortAnimTime)
					.alpha(show ? 0 : 1)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							loginFormView.setVisibility(show ? View.GONE
									: View.VISIBLE);
						}
					});
		} else {
			// The ViewPropertyAnimator APIs are not available, so simply show
			// and hide the relevant UI components.
			loginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
			loginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
		}
	}

	/**
	 * Represents an asynchronous login/sync/logout task used to authenticate
	 * the user and show id in to ticket scanner.
	 */
	public class AsyncConnectionToWebservice extends
			AsyncTask<Void, Void, Boolean> {
		// String that hold type of task to carry
		private String taskType;

		// Set task type with constructor here
		public AsyncConnectionToWebservice(String taskTypeName) {
			taskType = taskTypeName;
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			// TODO: attempt authentication against a network service.
			// use assetManager to read from local xml file stored inside asset
			// of apk
			/*
			 * AssetManager assetManager = getAssets(); InputStream instream =
			 * null; try { instream = assetManager.open("login.xml"); } catch
			 * (IOException e) { e.printStackTrace(); return false; }
			 * 
			 * DocumentBuilderFactory dbf =
			 * DocumentBuilderFactory.newInstance();
			 * 
			 * try {
			 * 
			 * DocumentBuilder db = dbf.newDocumentBuilder();
			 * 
			 * InputSource is = new InputSource(); is.setByteStream(instream);
			 * xmlDoc = db.parse(is);
			 * 
			 * } catch (ParserConfigurationException e) { Log.e("Error: ",
			 * e.getMessage()); return false; } catch (SAXException e) {
			 * Log.e("Error: ", e.getMessage()); return false; } catch
			 * (IOException e) { Log.e("Error: ", e.getMessage()); return false;
			 * }
			 */

			// For Network Connection and get data from Web service
			Document xmlDoc = null;
			URL webURL = null;
			String xmlDataString = null;
			if (taskType.equals("LoginTask")) {
				try {
					webURL = new URL(serverString + "TicketLogin/"
							+ userNameStr + "/" + passwordStr + "/" + showIdStr);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			} else if (taskType.equals("LogoutTask") // for SyncTask and
														// LogoutTask
					|| taskType.equals("SyncTask")) {
				// use following code to Read file from Internal Storage
				FileInputStream fis = null;
				String content = "";
				try {
					fis = getApplicationContext().openFileInput(
							showIdStr + ".tsc");
					byte[] input = new byte[fis.available()];
					while (fis.read(input) != -1) {
					}
					content += new String(input);
				} catch (FileNotFoundException e) {

					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				// Build URL for sync or logout
				try {
					String xmldata = URLEncoder.encode("<TicketScanner>"
							+ content + "</TicketScanner>", "UTF-8");
					if (taskType.equals("SyncTask")) {
						webURL = new URL(serverString + "TicketSync/"
								+ userNameStr + "/" + passwordStr + "/"
								+ showIdStr + "/?xml=" + xmldata);
					} else if (taskType.equals("LogoutTask")) {
						webURL = new URL(serverString + "TicketLogout/"
								+ userNameStr + "/" + passwordStr + "/"
								+ showIdStr + "/?xml=" + xmldata);
					}
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else
				return false;
			xmlDataString = connectToWebService(webURL);
			// on login if earlier data that is not uploaded
			// it can be only recovered if showID is same as previous one.
			if (taskType.equals("LoginTask")) {
				String[] fileList = getApplicationContext().fileList();
				for (int i = 0; i < fileList.length; i++) {
					if (fileList[i].contains(".tsc")) {
						if (!fileList[i].equals(showIdStr + ".tsc")) {
							// deleting unUsed files..
							getApplicationContext().deleteFile(fileList[0]);
						}
					}
				}
			}
			// if any data arrived from the web service
			if (xmlDataString == null) {
				setErrorMessageStr("Connection Problem Or Contact Administration");
				return false;
			}
			// Convert data from webService to XML data
			xmlDoc = toXML(xmlDataString);
			// From XML data to Data Structure for performance Booster
			if (!toDataStructure(xmlDoc))
				return false;
			// On Sync and LogOut delete the uploaded file
			if (taskType.equals("LogoutTask") || taskType.equals("SyncTask")) {
				getApplicationContext().deleteFile(showIdStr + ".tsc");
			}
			return true;
		}

		@Override
		protected void onPostExecute(final Boolean success) {
			if (taskType.equals("LoginTask")) {
				logInTask = null;
				showProgress(false);
				if (success) {
					setContentView(scanAreaRLayout);
					contentViewStr = "rootView";
				} else {
					if (getErrorMessageStr().contains("User")) {
						userNameEditText.setError(getErrorMessageStr());
						userNameEditText.requestFocus();
					} else if (getErrorMessageStr().contains("Password")) {
						passwordEditText.setError(getErrorMessageStr());
						passwordEditText.requestFocus();
					} else if (getErrorMessageStr().contains("Show")) {
						showIdEditText.setError(getErrorMessageStr());
						showIdEditText.requestFocus();
					} else if (getErrorMessageStr().contains("Connection")) {
						Toast toast = Toast.makeText(getBaseContext(),
								getErrorMessageStr(), 2000);
						toast.show();
					}
				}
			} else if (taskType.equals("SyncTask")) {
				syncTask = null;
				showProgress(false);
				if (success) {
					softTicketScanned = 0;
					setContentView(scanAreaRLayout);
					contentViewStr = "rootView";
				} else {
					if (getErrorMessageStr().contains("User")) {
						userNameEditText.setError(getErrorMessageStr());
						userNameEditText.requestFocus();
					} else if (getErrorMessageStr().contains("Password")) {
						passwordEditText.setError(getErrorMessageStr());
						passwordEditText.requestFocus();
					} else if (getErrorMessageStr().contains("Show")) {
						showIdEditText.setError(getErrorMessageStr());
						showIdEditText.requestFocus();
					} else if (getErrorMessageStr().contains("Connection")) {
						Toast toast = Toast.makeText(getBaseContext(),
								getErrorMessageStr(), 2000);
						toast.show();
					}
				}
			} else if (taskType.equals("LogoutTask")) {
				logoutTask = null;
				softTicketScanned = 0;
				showProgress(false);
				if (success) {
					viewTotalTicketsToscanTextView.setText("" + totalTickets);
					viewTotalTicketsScannedOnlineTextView.setText(""
							+ scannedTickets);
					viewTotalHardTicketsScannedTextView.setText(""
							+ hardTicketScanned);
					viewTotalSoftTicketsScannedTextView.setText(""
							+ softTicketScanned);
					summaryTitleTextView.setText("End Of Show" + "Summary Of"
							+ "\n" + getShowName());
					summaryTitleTextView.setGravity(Gravity.CENTER_VERTICAL
							| Gravity.CENTER_HORIZONTAL);
					setContentView(summaryRlayout);
					contentViewStr = "summaryView";
					bottomNotification
							.setText("Data is Uploaded Online\nPlease Login to Computer for more Information");
					bottomNotification.setGravity(Gravity.CENTER_VERTICAL
							| Gravity.CENTER_HORIZONTAL);
					closeButton.setText("Exit");
				} else {
					if (getErrorMessageStr().contains("User")) {
						userNameEditText.setError(getErrorMessageStr());
						userNameEditText.requestFocus();
					} else if (getErrorMessageStr().contains("Password")) {
						passwordEditText.setError(getErrorMessageStr());
						passwordEditText.requestFocus();
					} else if (getErrorMessageStr().contains("Show")) {
						showIdEditText.setError(getErrorMessageStr());
						showIdEditText.requestFocus();
					} else if (getErrorMessageStr().contains("Connection")) {
						Toast toast = Toast.makeText(getBaseContext(),
								getErrorMessageStr(), 2000);
						toast.show();
					}
				}
			}
		}

		@Override
		protected void onCancelled() {
			if (taskType.equals("LoginTask")) {
				logInTask = null;
				showProgress(false);
			} else if (taskType.equals("SyncTask")) {
				syncTask = null;
				showProgress(false);
			} else if (taskType.equals("LogoutTask")) {
				logoutTask = null;
				showProgress(false);
			}
		}
	}
}
