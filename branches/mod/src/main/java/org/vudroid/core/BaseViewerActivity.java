package org.vudroid.core;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.vudroid.core.events.CurrentPageListener;
import org.vudroid.core.events.DecodingProgressListener;
import org.vudroid.core.models.CurrentPageModel;
import org.vudroid.core.models.DecodingProgressModel;
import org.vudroid.core.models.ZoomModel;
import org.vudroid.core.views.PageViewZoomControls;

public abstract class BaseViewerActivity extends Activity implements DecodingProgressListener, CurrentPageListener
{
    private static final int MENU_EXIT = 0;
    private static final int MENU_GOTO = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int DIALOG_GOTO = 0;
    private static final String DOCUMENT_VIEW_STATE_PREFERENCES = "DjvuDocumentViewState";
    private DecodeService decodeService;
    private DocumentView documentView;
    private Toast pageNumberToast;
    private CurrentPageModel currentPageModel;
    
    //TODO: get this constants from array
    
    public static final String ROTATION_AUTOMATIC = "Automatic";
    public static final String ROTATION_LANDSCAPE = "Force landscape";
    public static final String ROTATION_PORTRAIT = "Force portrait";
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        initDecodeService();
        final ZoomModel zoomModel = new ZoomModel();
        final DecodingProgressModel progressModel = new DecodingProgressModel();
        progressModel.addEventListener(this);
        currentPageModel = new CurrentPageModel();
        currentPageModel.addEventListener(this);
        documentView = new DocumentView(this, zoomModel, progressModel, currentPageModel);
        zoomModel.addEventListener(documentView);
        documentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        decodeService.setContentResolver(getContentResolver());
        decodeService.setContainerView(documentView);
        documentView.setDecodeService(decodeService);   
        final ViewerPreferences viewerPreferences = new ViewerPreferences(this); 
        try
        {
        		decodeService.open(getIntent().getData()); 
        }
        catch(Exception e) 
        {
        	Toast.makeText(this, e.getMessage(), 300).show(); 
        	finish();
        	viewerPreferences.delRecent(getIntent().getData()); 
        	return;
        }

        final FrameLayout frameLayout = createMainContainer();
        frameLayout.addView(documentView);
        frameLayout.addView(createZoomControls(zoomModel));
        setShowTitle();
        setContentView(frameLayout);

        final SharedPreferences sharedPreferences = getSharedPreferences(DOCUMENT_VIEW_STATE_PREFERENCES, 0);
        documentView.goToPage(sharedPreferences.getInt(getIntent().getData().toString(), 0));
        documentView.showDocument();

        viewerPreferences.addRecent(getIntent().getData());
    }

    public void decodingProgressChanged(final int currentlyDecoding)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS, currentlyDecoding == 0 ? 10000 : currentlyDecoding);
            }
        });
    }

    public void currentPageChanged(int pageIndex)
    {
        final String pageText = (pageIndex + 1) + "/" + decodeService.getPageCount();
        if (pageNumberToast != null)
        {
            pageNumberToast.setText(pageText);
        }
        else
        {
            pageNumberToast = Toast.makeText(this, pageText, 300);
        }
        pageNumberToast.setGravity(Gravity.TOP | Gravity.LEFT,0,0);
        pageNumberToast.show();
        saveCurrentPage();
    }

    private void setWindowTitle()
    {
        final String name = getIntent().getData().getLastPathSegment();
        getWindow().setTitle(name);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
        setWindowTitle();
    }

    private void setShowTitle()
    {
    	 if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("title", false))
         {
             getWindow().requestFeature(Window.FEATURE_NO_TITLE);
         }
         else
         {
             getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
         }
    }
    
    private void setFullScreen()
    {
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("fullscreen", false))
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else
        	getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private PageViewZoomControls createZoomControls(ZoomModel zoomModel)
    {
        final PageViewZoomControls controls = new PageViewZoomControls(this, zoomModel);
        controls.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        zoomModel.addEventListener(controls);
        return controls;
    }

    private FrameLayout createMainContainer()
    {
        return new FrameLayout(this);
    }

    private void initDecodeService()
    {
        if (decodeService == null)
        {
            decodeService = createDecodeService();
        }
    }

    protected abstract DecodeService createDecodeService();

    @Override
    protected void onStop()
    {
        super.onStop();
    }


    private void setOrientation() 
    {
		String rotate = PreferenceManager.getDefaultSharedPreferences(this).getString("rotation","");
		if (ROTATION_LANDSCAPE.equals(rotate)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		} else if (ROTATION_PORTRAIT.equals(rotate)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		setOrientation();
		setFullScreen();
	}

	@Override
    protected void onDestroy() {
	    decodeService.recycle();
	    decodeService = null;
	    super.onDestroy();
	}

    private void saveCurrentPage()
    {
        final SharedPreferences sharedPreferences = getSharedPreferences(DOCUMENT_VIEW_STATE_PREFERENCES, 0);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(getIntent().getData().toString(), documentView.getCurrentPage());
        editor.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	final MenuItem menuExit = menu.add(0, MENU_EXIT, 0, "Exit");
    	menuExit.setAlphabeticShortcut('e');
    	final MenuItem menuGoto = menu.add(0, MENU_GOTO, 0, "Go to page");
    	menuGoto.setAlphabeticShortcut('g');
    	MenuItem menuSettings = menu.add(0, MENU_SETTINGS, 0, "Settings");
    	menuSettings.setAlphabeticShortcut('s');
    	menuSettings.setIntent(new Intent(this, SettingsActivity.class));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case MENU_EXIT:
                System.exit(0);
                return true;
            case MENU_GOTO:
                showDialog(DIALOG_GOTO);
                return true;
       //     case MENU_SETTINGS:
//                item.setChecked(!item.isChecked());
//                setFullScreenMenuItemText(item);
  //              viewerPreferences.setFullScreen(item.isChecked());

    //            finish();
      //          startActivity(getIntent());
          //      return true;
        }
        return false;
        //return super.onOptionsItemSelected(item);
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        switch (id)
        {
            case DIALOG_GOTO:
                return new GoToPageDialog(this, documentView, decodeService);
        }
        return null;
    }
}
