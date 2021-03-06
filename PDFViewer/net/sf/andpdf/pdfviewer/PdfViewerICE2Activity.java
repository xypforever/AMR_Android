package net.sf.andpdf.pdfviewer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.sf.andpdf.nio.ByteBuffer;
import net.sf.andpdf.pdfviewer.gui.FullScrollView;
import net.sf.andpdf.refs.HardReference;
import android.app.Activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.FloatMath;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mplatforma.amr.AttachmentsListActivity;
import com.mplatforma.amr.BookmarksActivity;
import com.mplatforma.amr.R;
import com.mplatforma.amr.ShelfBooksActivity;
import com.mplatforma.amr.VideoPlayer;
import com.mplatforma.amr.server.AttachmentDTO;
import com.mplatforma.amr.server.PageDTO;
import com.mplatforma.amr.server.ServerConnector;
import com.sun.pdfview.PDFFile;
import com.sun.pdfview.PDFImage;
import com.sun.pdfview.PDFPage;
import com.sun.pdfview.PDFPaint;
import com.sun.pdfview.decrypt.PDFAuthenticationFailureException;
import com.sun.pdfview.decrypt.PDFPassword;
import com.sun.pdfview.font.PDFFont;

import db.Book;
import db.Bookmark;
import db.DataHelper;


/**
 * U:\Android\android-sdk-windows-1.5_r1\tools\adb push u:\Android\simple_T.pdf /data/test.pdf
 * @author ferenc.hechler
 */
public class PdfViewerICE2Activity extends Activity{

	private DataHelper dhlpr;
	
	private static int STARTPAGE = 1;
	private static final float STARTZOOM = 1.0f;
	
	private static final String TAG = "PDFVIEWER";
	
	private final static int MEN_NEXT_PAGE = 1;
	private final static int MEN_PREV_PAGE = 2;
	private final static int MEN_GOTO_PAGE = 3;
	private final static int MEN_ZOOM_IN   = 4;
	private final static int MEN_ZOOM_OUT  = 5;
	private final static int MEN_BACK      = 6;
	private final static int MEN_CLEANUP   = 7;
	private final static int MEN_ATTS   = 8;
	private final static int MEN_BOOKMARKS   = 9;
	private final static int MEN_CREATE_BOOKMARK   = 10;
	private final static int MEN_DELETE_BOOKMARK   = 11;
	
	
	
	
	private final static int DIALOG_PAGENUM = 1;
	private final static int DIALOG_LOAD = 2;
	private final static int DIALOG_UNZIPPING = 3;
	
	
	private GraphView mOldGraphView;
	private GraphView mGraphView;
	private Integer pdf_id;
	private boolean mPdfLoaded = false;
	private int mPage;
	private float mZoom;
    private File mTmpFile;
    
    private Map<Integer,PDFPage> page_map = new HashMap<Integer, PDFPage>();
    private Thread backgroundThread;
    private Thread bckgrCacheThread = null;
    private Handler uiHandler;
    private boolean has_bookmark=false;
    private List<Integer> pages_IDS = new ArrayList<Integer>();
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		// return a reference to the current instance
		Log.e(TAG, "onRetainNonConfigurationInstance");
		return this;
	}
	/**
	 * restore member variables from previously saved instance
	 * @see onRetainNonConfigurationInstance
	 * @return true if instance to restore from was found
	 */
	private boolean restoreInstance() {
		mOldGraphView = null;
		Log.e(TAG, "restoreInstance");
		if (getLastNonConfigurationInstance()==null)
			return false;
		PdfViewerICE2Activity inst =(PdfViewerICE2Activity)getLastNonConfigurationInstance();
		if (inst != this) {
			Log.e(TAG, "restoring Instance");
//			mOldGraphView = inst.mGraphView;
//			mPage = inst.mPage;
//			mPdfLoaded = inst.mPdfLoaded;
//			//mPdfPage = inst.mPdfPage;
//			mTmpFile = inst.mTmpFile;
//			mZoom = inst.mZoom;
//			pdf_id = inst.pdf_id;
//			backgroundThread = inst.backgroundThread; 
			// mGraphView.invalidate();
		}	
		return true;
	}

	/** Called when the activity is first created. */
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dhlpr = new DataHelper(this);
        //this.dhlpr.deleteAll();
        uiHandler = new Handler();
        restoreInstance();
        if (mOldGraphView != null) {
	        mGraphView = new GraphView(this);
	        mGraphView.fileMillis = mOldGraphView.fileMillis;
	        mGraphView.mBi = mOldGraphView.mBi;
	        mGraphView.mLine1 = mOldGraphView.mLine1;
	        mGraphView.mLine2 = mOldGraphView.mLine2;
	        mGraphView.mLine3 = mOldGraphView.mLine3;
	        mGraphView.mText = mOldGraphView.mText;
	        mGraphView.pageParseMillis= mOldGraphView.pageParseMillis;
	        mGraphView.pageRenderMillis= mOldGraphView.pageRenderMillis;
	        mOldGraphView = null;
	        //mGraphView.mImageView.setImageBitmap(mGraphView.mBi);
	       // mGraphView.updateTexts();
	        setContentView(mGraphView);
        }
        else {
	        mGraphView = new GraphView(this);	        
	        Intent intent = getIntent();
	        Log.i(TAG, ""+intent);

	        boolean showImages = getIntent().getBooleanExtra(PdfFileSelectActivity.EXTRA_SHOWIMAGES, PdfFileSelectActivity.DEFAULTSHOWIMAGES);
	        PDFImage.sShowImages = showImages;
	        boolean antiAlias = getIntent().getBooleanExtra(PdfFileSelectActivity.EXTRA_ANTIALIAS, PdfFileSelectActivity.DEFAULTANTIALIAS);
	        PDFPaint.s_doAntiAlias = antiAlias;
	    	boolean useFontSubstitution = getIntent().getBooleanExtra(PdfFileSelectActivity.EXTRA_USEFONTSUBSTITUTION, PdfFileSelectActivity.DEFAULTUSEFONTSUBSTITUTION);
	        PDFFont.sUseFontSubstitution= useFontSubstitution;
	    	boolean keepCaches = getIntent().getBooleanExtra(PdfFileSelectActivity.EXTRA_KEEPCACHES, PdfFileSelectActivity.DEFAULTKEEPCACHES);
	        HardReference.sKeepCaches= keepCaches;
	        int toPage = getIntent().getIntExtra("topage",1);
		    STARTPAGE = toPage;    
	        if (intent != null) {
	        	if ("android.intent.action.VIEW".equals(intent.getAction())) {
        			//pdf_id = storeUriContentToFile(intent.getData());
	        	}
	        	else {
	                pdf_id = getIntent().getIntExtra(PdfFileSelectActivity.EXTRA_PDF_ID,0);
	        	}
	        }
	        
	        //if (pdf_id == 0)
	        	//pdffilename = "no file selected";

			mPage = STARTPAGE;
			mZoom = STARTZOOM;

			setContent(null);
	        
        }
    }
    	    

	private void setContent(String password) {
        try { 
        //	showDialog(DIALOG_LOAD);
    		loadPDF(pdf_id,password);
	       
	        //dismissDialog(DIALOG_LOAD);
    	}
        catch (PDFAuthenticationFailureException e) {
        	setContentView(R.layout.pdf_file_password);
           	final EditText etPW= (EditText) findViewById(R.id.etPassword);
           	Button btOK= (Button) findViewById(R.id.btOK);
        	Button btExit = (Button) findViewById(R.id.btExit);
            btOK.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					String pw = etPW.getText().toString();
		        	setContent(pw);
				}
			});
            btExit.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finish();
				}
			});
        }
	}
	private synchronized void startRenderThread(final int page, final float zoom) {
//		if (backgroundThread != null)
//			return;
//        backgroundThread = new Thread(new Runnable() {
//			@Override
//			public void run() {
//				try {
//			        if (mPdfLoaded != false) {
//			        	showPage(page, zoom);
//			        }
//				} catch (Exception e) {
//					Log.e(TAG, e.getMessage(), e);
//				}
//		        backgroundThread = null;
//			}
//		});
//        updateImageStatus();
//        backgroundThread.start();
		try {
			showPage(page, zoom);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	private void updateImageStatus() {
//		Log.i(TAG, "updateImageStatus: " +  (System.currentTimeMillis()&0xffff));
		if (backgroundThread == null) {
			///mGraphView.updateUi();
			return;
		}
		//mGraphView.updateUi();
		mGraphView.postDelayed(new Runnable() {
			@Override public void run() {
				updateImageStatus();
			}
		}, 1000);
	}
	
	private void createOptMenu(Menu menu)
	{
		
		has_bookmark = false;
		ArrayList<Bookmark> bmaks = dhlpr.getBookmarks(new Book(pdf_id,0,null,null,0,null));
        for(int i=0; i < bmaks.size();i++)
        {
        	if(current_page_number == bmaks.get(i).getPage()) {has_bookmark=true;break;}
        	if(bmaks.get(i).getPage() <=0) dhlpr.delete_bookmark(bmaks.get(i).getID(),pdf_id);
        }
        menu.clear();
		 menu.add(Menu.NONE, MEN_GOTO_PAGE, Menu.NONE, "До стор.");
	        //menu.add(Menu.NONE, MEN_ZOOM_OUT, Menu.NONE, "Zoom Out");
	        //menu.add(Menu.NONE, MEN_ZOOM_IN, Menu.NONE, "Zoom In");
	        menu.add(Menu.NONE, MEN_BACK, Menu.NONE, "Назад");
	        menu.add(Menu.NONE, MEN_ATTS, Menu.NONE, "Додатки");
	        menu.add(Menu.NONE, MEN_BOOKMARKS, Menu.NONE, "Закладки");
	        if(!has_bookmark)
	        	menu.add(Menu.NONE, MEN_CREATE_BOOKMARK, Menu.NONE, "Додати закл.");
	        else
	         	menu.add(Menu.NONE, MEN_DELETE_BOOKMARK, Menu.NONE, "Видалити закл.");
	         	
//	        if (HardReference.sKeepCaches)
//	            menu.add(Menu.NONE, MEN_CLEANUP, Menu.NONE, "Clear Caches");

	}
	private Menu men = null;
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        //menu.add(Menu.NONE, MEN_PREV_PAGE, Menu.NONE, "Previous Page");
        //menu.add(Menu.NONE, MEN_NEXT_PAGE, Menu.NONE, "Next Page");
       
        createOptMenu(menu);
        men = menu;
       return true;
    }
    
    /**
     * Called when a menu item is selected.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
    	switch (item.getItemId()) {
    	case MEN_NEXT_PAGE: {
    		nextPage();
    		break;
    	}
    	case MEN_PREV_PAGE: {
    		prevPage();
    		break;
    	}
    	case MEN_GOTO_PAGE: {
    		gotoPage();
    		break;
    	}
    	case MEN_ZOOM_IN: {
    		zoomIn();
    		break;
    	}
    	case MEN_ZOOM_OUT: {
    		zoomOut();
    		break;
    	}
    	case MEN_BACK: {
            finish();
            break;
    	}
    	case MEN_CLEANUP: {
            HardReference.cleanup();
            break;
    	}
    	case MEN_ATTS: {
            showAttsList();
            break;
    	}
    	case MEN_BOOKMARKS: {
            showBMList();
            break;
    	}
    	case MEN_CREATE_BOOKMARK: {
            doCreateBookmark();
            break;
    	}
    	case MEN_DELETE_BOOKMARK: {
            doDeleteBookmark();
            break;
    	}
    	}
    	return true;
    }
    private void doCreateBookmark()
    {
    	dhlpr.insert_bookmark(pdf_id, current_page_number, "");
    	createOptMenu(men);
    }
    private void doDeleteBookmark()
    {
    	dhlpr.delete_bookmark(current_page_number,pdf_id);
    	createOptMenu(men);
    }
    private void showAttsList()
    {
    	int page_id = pages_IDS.get(mPage);
    	Intent intent = new Intent(this,AttachmentsListActivity.class)
		.putExtra("PAGE_ID", page_id);
		startActivityForResult(intent,1); 
    	
    }
    private void showBMList()
    {
    	Intent intent = new Intent(this,BookmarksActivity.class)
		.putExtra("BOOK_ID", pdf_id);
		startActivityForResult(intent,1); 
    }
    private void zoomIn() {
    	if (mPdfLoaded != false) {
    		if (mZoom < 4) {
    			mZoom *= 1.5;
    			if (mZoom > 4)
    				mZoom = 4;
    			startRenderThread(mPage, mZoom);
    		}
    	}
	}

    private void zoomOut() {
    	if (mPdfLoaded != false) {
    		if (mZoom > 0.25) {
    			mZoom /= 1.5;
    			if (mZoom < 0.25)
    				mZoom = 0.25f;
    			startRenderThread(mPage, mZoom);
    		}
    	}
	}

	private void nextPage() {
    	if (mPdfLoaded != false) {
    		if (mPage < pages_IDS.size()) {
    			mPage += 1;
    			startRenderThread(mPage, mZoom);
    		}
    	}
	}

    private void prevPage() {
    	if (mPdfLoaded != false) {
    		if (mPage > 1) {
    			mPage -= 1;
    			startRenderThread(mPage, mZoom);
    		}
    	}
	}
    
	private void gotoPage() {
    	if (mPdfLoaded != false) {
            showDialog(DIALOG_PAGENUM);
    	}
	}

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_PAGENUM:
	        LayoutInflater factory = LayoutInflater.from(this);
	        final View pagenumView = factory.inflate(R.layout.dialog_pagenumber, null);
			final EditText edPagenum = (EditText)pagenumView.findViewById(R.id.pagenum_edit);
			edPagenum.setText(Integer.toString(mPage));
	        return new AlertDialog.Builder(this)
	            .setIcon(R.drawable.icon)
	            .setTitle("Jump to page")
	            .setView(pagenumView)
	            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	            		String strPagenum = edPagenum.getText().toString();
	            		int pageNum = mPage;
	            		try {
	            			pageNum = Integer.parseInt(strPagenum);
	            		}
	            		catch (NumberFormatException ignore) {}
	            		if ((pageNum!=mPage) && (pageNum>=1) && (pageNum <= pages_IDS.size())) {
	            			mPage = pageNum;
	            			startRenderThread(mPage, mZoom);
	            		}
	                }
	            })
	            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	                }
	            })
	            .create();
	        
        case DIALOG_LOAD:
	        //LayoutInflater factory2 = LayoutInflater.from(this);
	       // final View pagenumView2 = factory2.inflate(R.layout.dialog_load, null);
	        
        	progressLoadPDFDialog = new ProgressDialog(this);
        	progressLoadPDFDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        	progressLoadPDFDialog.setMessage("Loading...");
        	progressLoadPDFDialog.setMax(100);
        	progressLoadPDFDialog.setCancelable(false);
			return progressLoadPDFDialog;
        case DIALOG_UNZIPPING:
        	progressUnzipPDFDialog = new ProgressDialog(this);
        	progressUnzipPDFDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        	progressUnzipPDFDialog.setMessage("Loading...");
        	progressUnzipPDFDialog.setCancelable(false);
        	progressUnzipPDFDialog.setMax(100);
			return progressUnzipPDFDialog;
        	
        }        
        return null;
    }
    ProgressDialog progressLoadPDFDialog;
    ProgressDialog progressUnzipPDFDialog;
    ProgressDialog progressLoadATTDialog;
    
	private class GraphView extends FullScrollView implements OnTouchListener{
    	private String mText;
    	private long fileMillis;
    	private long pageParseMillis;
    	private long pageRenderMillis;
    	private Bitmap mBi;
    	private String mLine1;
    	private String mLine2;
    	private String mLine3;
    	private ImageView mImageView;
    	private TextView mLine1View; 
    	private TextView mLine2View; 
    	private TextView mLine3View; 
    	private Button mBtPage;
    	private Button mBtPage2;
    	
    	private ImageButton bb;
    	public void removeExtBtn()
    	{
    		vl.removeView(bb);
    	}
    	
       // private ProgressBar bar;
    	public void addExtBtn(ImageButton b)
    	{
    			this.bb = b;
    			vl.addView(bb);
    		    	}
        protected void setFileLoaded()
        {
        	//bar.setVisibility(ProgressBar.INVISIBLE);
        }
        Matrix matrix = new Matrix();
        Matrix savedMatrix = new Matrix();
        PointF start = new PointF();
        PointF mid = new PointF();
        Float oldDist = (float) 0;

        // We can be in one of these 3 states
        static final int NONE = 0;
        static final int DRAG = 1;
        static final int ZOOM = 2;
        int mode = NONE;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
           CoolLayout vie = (CoolLayout) v;
           ImageView view = vie.getImageView();
           // Dump touch event to log
           dumpEvent(event);

           if(event.getAction() == MotionEvent.EDGE_LEFT)
        	   prevPage();
           if(event.getAction() == MotionEvent.EDGE_RIGHT)
        	   nextPage();
           
           // Handle touch events here...
           switch (event.getAction() & MotionEvent.ACTION_MASK) {
//           case MotionEvent.EDGE_LEFT:
//        	   prevPage();
//        	   break;
//           case MotionEvent.EDGE_RIGHT:
//        	   nextPage();
//        	   break;
           case MotionEvent.ACTION_DOWN:
               
        	  savedMatrix.set(matrix);
              start.set(event.getX(), event.getY());
              Log.d(TAG, "mode=DRAG" );
              mode = DRAG;
              
              break;
           case MotionEvent.ACTION_UP:
           case MotionEvent.ACTION_POINTER_UP:
              mode = NONE;
              Log.d(TAG, "mode=NONE" );
              if(Math.abs(event.getX()-start.x) < 10 && Math.abs(event.getY()-start.y) < 10)
              {
            	  if(event.getX() < getWindowManager().getDefaultDisplay().getWidth()/2)
            	  {
            		  prevPage();
            	  }else
            	  {
            		  nextPage();
            	  }
              }
              break;
           case MotionEvent.ACTION_POINTER_DOWN:
         	   oldDist = spacing(event);
         	   Log.d(TAG, "oldDist=" + oldDist);
         	   if (oldDist > 10f) {
         	      savedMatrix.set(matrix);
         	      midPoint(mid, event);
         	      mode = ZOOM;
         	      Log.d(TAG, "mode=ZOOM" );
         	   }
         	   break;

         	case MotionEvent.ACTION_MOVE:
         	   if (mode == DRAG) {
         		   matrix.set(savedMatrix);
                    matrix.postTranslate(event.getX() - start.x,
                    event.getY() - start.y);
         	   }
         	   else if (mode == ZOOM) {
         	      float newDist = spacing(event);
         	      Log.d(TAG, "newDist=" + newDist);
         	      if (newDist > 10f) {
         	         matrix.set(savedMatrix);
         	         float scale = newDist / oldDist;
         	         matrix.postScale(scale, scale, mid.x, mid.y);
         	      }
         	   }
         	   break;
        }

           // Perform the transformation
           view.setImageMatrix(matrix);

           return true; // indicate event was handled
        }
        
        
        private void midPoint(PointF point, MotionEvent event) {
     	   float x = event.getX(0) + event.getX(1);
     	   float y = event.getY(0) + event.getY(1);
     	   point.set(x / 2, y / 2);
     	}

        private float spacing(MotionEvent event) {
     	   float x = event.getX(0) - event.getX(1);
     	   float y = event.getY(0) - event.getY(1);
     	   return FloatMath.sqrt(x * x + y * y);
     	}

     /** Show an event in the LogCat view, for debugging */
        private void dumpEvent(MotionEvent event) {
           String names[] = { "DOWN" , "UP" , "MOVE" , "CANCEL" , "OUTSIDE" ,
              "POINTER_DOWN" , "POINTER_UP" , "7?" , "8?" , "9?" };
           StringBuilder sb = new StringBuilder();
           int action = event.getAction();
           int actionCode = action & MotionEvent.ACTION_MASK;
           sb.append("event ACTION_" ).append(names[actionCode]);
           if (actionCode == MotionEvent.ACTION_POINTER_DOWN
                 || actionCode == MotionEvent.ACTION_POINTER_UP) {
              sb.append("(pid " ).append(
              action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
              sb.append(")" );
           }
           sb.append("[" );
           for (int i = 0; i < event.getPointerCount(); i++) {
              sb.append("#" ).append(i);
              sb.append("(pid " ).append(event.getPointerId(i));
              sb.append(")=" ).append((int) event.getX(i));
              sb.append("," ).append((int) event.getY(i));
              if (i + 1 < event.getPointerCount())
                 sb.append(";" );
           }
           sb.append("]" );
           Log.d(TAG, sb.toString());
        }
        class CoolLayout extends FrameLayout
        {
        	private ImageView img;
			public CoolLayout(Context context) {
				super(context);
				
			}
			public ImageView getImageView()
			{
				return img;
			}
			public void  setImageView(ImageView img)
			{
				this.img = img;
				addView(this.img);
			}
			
        	
        }
        
        private CoolLayout vl;
        public GraphView(Context context) {
            super(context);

            // layout params
			LinearLayout.LayoutParams lpWrap1 = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,1);
			LinearLayout.LayoutParams lpWrap10 = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,10);

            // vertical layout
			FrameLayout fr = new FrameLayout(context);
			vl=new CoolLayout(context);
			vl.setLayoutParams(lpWrap10);
			//vl.setOrientation(LinearLayout.VERTICAL);
			
//			 bar = new ProgressBar(getContext());
//			bar.setVisibility(ProgressBar.VISIBLE);
//			bar.setMax(30);
//			bar.setSoundEffectsEnabled(true);
//			bar.setLayoutParams(new LayoutParams(150, 150));
//			//bar.
//			vl.addView(bar);
				mLine1 = "PDF Viewer initializing";
				mLine1View = new TextView(context);
		        mLine1View.setLayoutParams(lpWrap1);
		        mLine1View.setText(mLine1);
		        mLine1View.setTextColor(Color.BLACK);
		       // vl.addView(mLine1View);
			
				mLine2 = "unknown number of pages";
				mLine2View = new TextView(context);
		        mLine2View.setLayoutParams(lpWrap1);
		        mLine2View.setText(mLine2);
		        mLine2View.setTextColor(Color.BLACK);
		        //vl.addView(mLine2View);
			
				mLine3 = "unknown timestamps";
				mLine3View = new TextView(context);
		        mLine3View.setLayoutParams(lpWrap1);
		        mLine3View.setText(mLine3);
		        mLine3View.setTextColor(Color.BLACK);
		       // vl.addView(mLine3View);
			
		        //addNavButtons(vl);
		        // remember page button for updates
		        mBtPage2 = mBtPage;
		        
		        mImageView = new ImageView(context);
		        setPageBitmap(null);
		       // updateImage();
		        mImageView.setLayoutParams(lpWrap1);
		        mImageView.setPadding(5, 5, 5, 5);
		        mImageView.setScaleType(ScaleType.MATRIX);
		        vl.setImageView(mImageView);	
		       // mImageView.setOnTouchListener(this);
		        //addNavButtons(vl);
			    
			setLayoutParams(new LayoutParams(
					LayoutParams.FILL_PARENT,
					LayoutParams.FILL_PARENT,
					100));
			setBackgroundColor(Color.LTGRAY);
			setHorizontalScrollBarEnabled(true);
			setHorizontalFadingEdgeEnabled(true);
			setVerticalScrollBarEnabled(true);
			setVerticalFadingEdgeEnabled(true);
			vl.setOnTouchListener(this);
			addView(vl);
			
        }

        private void addNavButtons(ViewGroup vg) {
        	
	        addSpace(vg, 6, 6);
	        
			LinearLayout.LayoutParams lpChild1 = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,1);
			LinearLayout.LayoutParams lpWrap10 = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,10);
        	
        	Context context = vg.getContext();
			LinearLayout hl=new LinearLayout(context);
			hl.setLayoutParams(lpWrap10);
			hl.setOrientation(LinearLayout.HORIZONTAL);

				// zoom out button
				Button bZoomOut=new Button(context);
				bZoomOut.setLayoutParams(lpChild1);
				bZoomOut.setText("-");
				bZoomOut.setWidth(40);
				bZoomOut.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
			            zoomOut();
					}
				});
		        hl.addView(bZoomOut);
		        
				// zoom in button
				Button bZoomIn=new Button(context);
				bZoomIn.setLayoutParams(lpChild1);
		        bZoomIn.setText("+");
		        bZoomIn.setWidth(40);
		        bZoomIn.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
			            zoomIn();
					}
				});
		        hl.addView(bZoomIn);
	    
		        addSpace(hl, 6, 6);
		        
				// prev button
				Button bPrev=new Button(context);
		        bPrev.setLayoutParams(lpChild1);
		        bPrev.setText("<");
		        bPrev.setWidth(40);
		        bPrev.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
			            prevPage();
					}
				});
		        hl.addView(bPrev);
        
				// page button
				mBtPage=new Button(context);
				mBtPage.setLayoutParams(lpChild1);
				String maxPage = ((mPdfLoaded==false)?"?":Integer.toString(pages_IDS.size()));
				mBtPage.setText(mPage+"/"+maxPage);
				mBtPage.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
			    		gotoPage();
					}
				});
		        hl.addView(mBtPage);
        
				// next button
				Button bNext=new Button(context);
		        bNext.setLayoutParams(lpChild1);
		        bNext.setText(">");
		        bNext.setWidth(40);
		        bNext.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
			    		nextPage();
					}
				});
		        hl.addView(bNext);
        
		        addSpace(hl, 20, 20);
        
				// exit button
				Button bExit=new Button(context);
		        bExit.setLayoutParams(lpChild1);
		        bExit.setText("Back");
		        bExit.setWidth(60);
		        bExit.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
			            finish();
					}
				});
		        hl.addView(bExit);
        	        
		        vg.addView(hl);
		    
	        addSpace(vg, 6, 6);
		}

		private void addSpace(ViewGroup vg, int width, int height) {
			TextView tvSpacer=new TextView(vg.getContext());
			tvSpacer.setLayoutParams(new LinearLayout.LayoutParams(width,height,1));
			tvSpacer.setText("");
//			tvSpacer.setWidth(width);
//			tvSpacer.setHeight(height);
	        vg.addView(tvSpacer);
    
		}

//		private void showText(String text) {
//        	Log.i(TAG, "ST='"+text+"'");
//        	mText = text;
//        	updateUi();
//		}
        
//        private void updateUi() {
//        	uiHandler.post(new Runnable() {
//				@Override
//				public void run() {
//		        	updateTexts();
//				}
//			});
//		}

        private void updateImage() {
        	uiHandler.post(new Runnable() {
				@Override
				public void run() {
		        	mImageView.setImageBitmap(mBi);
				}
			});
		}

		private void setPageBitmap(Bitmap bi) {
			if (bi != null)
				mBi = bi;
			else {
//				mBi = Bitmap.createBitmap(100, 100, Config.RGB_565);
//	            Canvas can = new Canvas(mBi);
//	            can.drawColor(Color.RED);
//	            
//				Paint paint = new Paint();
//	            paint.setColor(Color.BLUE);
//	            can.drawCircle(50, 50, 50, paint);
//	            
//	            paint.setStrokeWidth(0);
//	            paint.setColor(Color.BLACK);
//	            can.drawText("Bitmap", 10, 50, paint);
			}
		}
        
//		protected void updateTexts() {
//            mLine1 = "PdfViewer: "+mText;
//            float fileTime = fileMillis*0.001f;
//            float pageRenderTime = pageRenderMillis*0.001f;
//            float pageParseTime = pageParseMillis*0.001f;
//            mLine2 = "render page="+format(pageRenderTime,2)+", parse page="+format(pageParseTime,2)+", parse file="+format(fileTime,2);
//    		int maxCmds = PDFPage.getParsedCommands();
//    		int curCmd = PDFPage.getLastRenderedCommand()+1;
//    		mLine3 = "PDF-Commands: "+curCmd+"/"+maxCmds;
//    		mLine1View.setText(mLine1);
//    		mLine2View.setText(mLine2);
//    		mLine3View.setText(mLine3);
//    		if (mPdfPage != null) {
//	    		if (mBtPage != null)
//	    			mBtPage.setText(mPdfPage.getPageNumber()+"/"+mPdfLoaded.getNumPages());
//	    		if (mBtPage2 != null)
//	    			mBtPage2.setText(mPdfPage.getPageNumber()+"/"+mPdfLoaded.getNumPages());
//    		}
//        }

		private String format(double value, int num) {
			NumberFormat nf = NumberFormat.getNumberInstance();
			nf.setGroupingUsed(false);
			nf.setMaximumFractionDigits(num);
			String result = nf.format(value);
			return result;
		}
    }

	private int current_page_number = -1;
	private int cache_page_number = 0;
	
    private void showPage(int page, float zoom) throws Exception {
        long startTime = System.currentTimeMillis();
        long middleTime = startTime;
       // dismissDialog(DIALOG_LOAD);
    	try {
    		
     	     
     	  	        Bitmap bi = dhlpr.getPage(pages_IDS.get(page));
     	  	        current_page_number = page;
     	  	        if(men != null)onCreateOptionsMenu(men);
  	  	        	ArrayList<Bookmark> bmaks = dhlpr.getBookmarks(new Book(pdf_id,0,null,null,0,null));
     	  	        for(int i=0; i < bmaks.size();i++)
     	  	        {
     	  	        	if(current_page_number == bmaks.get(i).getPage()) {has_bookmark=true;break;}
     	  	        	if(bmaks.get(i).getPage() <=0) dhlpr.delete_bookmark(bmaks.get(i).getPage(),pdf_id);
     	  	        }
     	  	        List<AttachmentDTO> atts = dhlpr.getAttachments(pages_IDS.get(page));
     	  	        mZoom = (float)dhlpr.getZoom(page);
     	  	        mGraphView.setPageBitmap(bi);
     	  	        mGraphView.updateImage();
     	  	        if(atts.size()>0)
     	  	        {
     	  	        	
     	  	        	LayoutInflater inflater = PdfViewerICE2Activity.this.getLayoutInflater();  
     	                ImageButton extView = (ImageButton) inflater.inflate(R.layout.att_btn, null, true);  
     	               
     	  	        	//ImageButton b = (ImageButton)findViewById(R.id.att_btn_id);
     	  	             	  	            
     	  	        //	b.setBackgroundResource(R.id);
         	  	        extView.setOnClickListener(new OnClickListener() {
    						@Override
    						public void onClick(View v) {
    							showAttsList();
    						}
    					});
         	  	       mGraphView.addExtBtn(extView);
         	  	     }else
         	  	     {
         	  	    	 mGraphView.removeExtBtn();
         	  	     }
     	  	            	  	        
     	  	        
     	  	        
//     	  	        mPdfPage = mPdfLoaded.getPage(page, true);
//     	  	        if (cache_page_number - page < 2) 
//     	  	        	{
//     	  	        	if (!queue.contains(page+1))queue.add(page+1);
//     	  	        	if (!queue.contains(page+2))queue.add(page+2);
//     	  	        	//queue.add(page-1);
//     	  	        	//queue.add(page+2);
//     	  	        	}
//     	  	        //startcaching();
//    			 }else
//    			 {
//    				 current_page_number = page;
//      	        	 mPdfPage = mPdfLoaded.getPage(page, true);
//      	        	 //page_map.put(page, mPdfPage);
//    				 int num = mPdfPage.getPageNumber();
//    	  	  	        int maxNum = mPdfLoaded.getNumPages();
//    	  	  	        float wi = mPdfPage.getWidth();
//    	  	  	        float hei = mPdfPage.getHeight();
//    	  	  	        RectF clip = null;
//    	    		    Bitmap bi = mPdfPage.getImage((int)(wi*zoom), (int)(hei*zoom), clip, true, true);
//    	 	  	        mGraphView.setPageBitmap(bi);
//    	 	  	        mGraphView.updateImage();
//    	 	  	        
//    	 	  	      ByteArrayOutputStream out = new ByteArrayOutputStream();
//     	  	          bi.compress(Bitmap.CompressFormat.PNG, 100, out);
//     	  	          ContentValues cv = new ContentValues();
//     	  	          cv.put(DataHelper.KEY_IMG, out.toByteArray()); 
//     	  	          dhlpr.insert_bitmap(cv.getAsByteArray(DataHelper.KEY_IMG), zoom);
//     	  	          cache_page_number++;
//     	  	          
//     	  	       if (cache_page_number - page < 2) {
//    	  	        	if (!queue.contains(page+1))queue.add(page+1);
//    	  	        	if (!queue.contains(page+2))queue.add(page+2);
//    	  	        	//queue.add(page-1);
//    	  	        	//queue.add(page+2);
//    	  	        }
//    			 }
//    	        	
//    	     }
//    		 else
//    		 {
//    			//PDFPage pg = page_map.get(page);
//    			int num = mPdfPage.getPageNumber();
//  	  	        int maxNum = mPdfLoaded.getNumPages();
//  	  	        float wi = mPdfPage.getWidth();
//  	  	        float hei = mPdfPage.getHeight();
//  	  	        RectF clip = null;
//    		    Bitmap bi = mPdfPage.getImage((int)(wi*zoom), (int)(hei*zoom), clip, true, true);
// 	  	        mGraphView.setPageBitmap(bi);
// 	  	        mGraphView.updateImage();
// 	  	        
// 	  	        
//    		 }
	        // free memory from previous page
	      
		} catch (Throwable e) {
			Log.e(TAG, e.getMessage(), e);
			//mGraphView.showText("Exception: "+e.getMessage());
		}
        long stopTime = System.currentTimeMillis();
      
    }
    
    
    
    final Handler loadPDFhandler = new Handler() {
        public void handleMessage(Message msg) {
            // Get the current value of the variable total from the message data
            // and update the progress bar.
            int total = msg.getData().getInt("loaded");
            if (progressLoadPDFDialog!=null) progressLoadPDFDialog.setProgress(total);
            if (total >= 100 && progressLoadPDFDialog!=null){
                dismissDialog(DIALOG_LOAD);
                progressLoadPDFDialog = null;
                //progThread.setState(ProgressThread.DONE);
            }
        }
    };
    private void loadPDF(Integer book_id,String password) throws PDFAuthenticationFailureException {
       try {
    	   List<Integer> pages = dhlpr.getBookPageIDs(book_id);
    	   if (pages.size()>0)
    	   {
    		   Bitmap arr = dhlpr.getPage(dhlpr.getPageID(new PageDTO(pages.get(0),null)));
    		   	 if (arr == null)
             	 {
             		ServerConnector c = new ServerConnector(dhlpr);
             		//ArrayList<Book> books = dhlpr.getAllBooks();
             		showDialog(DIALOG_LOAD);
             		c.serverGetBook(loadPDFhandler,dhlpr.getBookExtID(book_id),new Commandir(book_id));
                	
             	 }
    		   	new Commandir(book_id).doCommand();
             	 //byte [] arr2 = dhlpr.getBookContents(book_id);
         	
    	        //openFile(book_id);
           }
    	  	
    	} catch (Throwable e) {
			e.printStackTrace();
			//mGraphView.showText("Exception: "+e.getMessage());
		}
    }
    
    
//    public class Commandir
//    {
//    	private int book_id;
//    	public void setHandlerBook(int book_id)
//    	{
//    		this.book_id = book_id;
//    	}
//    	public void handleMessage(Message m)
//    	{
//    		pages_IDS = dhlpr.getBookPageIDs(book_id);
//		   	 mPdfLoaded = true;
//		   	 startRenderThread(mPage, mZoom);
//		        setContentView(mGraphView);
//    	}
//    };
    public class Commandir extends Handler
    {
    	private int book_id;
    	public Commandir(int book_id)
    	{
    		this.book_id = book_id;
    	}
    	public void doCommand()
    	{
    		pages_IDS = dhlpr.getBookPageIDs(book_id);
		   	 mPdfLoaded = true;
		   	 startRenderThread(mPage, mZoom);
		        setContentView(mGraphView);
    	}
    	@Override
    	public void handleMessage(Message m)
    	{
    		doCommand();
    	}
    }
    
    /**
     * <p>Open a specific pdf file.  Creates a DocumentInfo from the file,
     * and opens that.</p>
     *
     * <p><b>Note:</b> Mapping the file locks the file until the PDFFile
     * is closed.</p>
     *
     * @param file the file to open
     * @throws IOException
     */
//    public void openFile(int id_book) throws IOException {
//      
//        startcaching(id_book);
//        /************************************************/
//    }
    
    private ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<Integer>();
//    private void startcaching(int id_book) {
//		if(bckgrCacheThread == null)
//    	{
//    		bckgrCacheThread = new Thread(new Runnable() {
//     			@Override
//     			public void run() {
//     				try {
//     					while (!Thread.currentThread().isInterrupted())
//     					{
//     							if (!queue.isEmpty())
//          			        	{
//          			        		for(int i = 0;i < queue.size();i++)
//          			        		{
//          			        			Integer page = queue.poll();
//          			        			PDFPage mPdfPage = mPdfLoaded.getPage(page, true);
//          			        			//page_map.put(page,mPdfPage);
//              			        		float wi = mPdfPage.getWidth();
//              		    	  	        float hei = mPdfPage.getHeight();
//              		    	  	        RectF clip = null;
//              		    	  	        Bitmap bi = mPdfPage.getImage((int)(wi), (int)(hei), clip, true, true);
//              		    	  	        ByteArrayOutputStream out = new ByteArrayOutputStream();
//              		    	  	        bi.compress(Bitmap.CompressFormat.PNG, 100, out);
//              		    	  	        ContentValues cv = new ContentValues();
//              		    	  	        cv.put(DataHelper.KEY_IMG, out.toByteArray()); 
//              		    	  	        dhlpr.insert_bitmap(cv.getAsByteArray(DataHelper.KEY_IMG),mZoom);
//              		    	  	        ++cache_page_number;  	  	        
//          			        		}
//          			        	}
//   
//     						 Thread.currentThread().sleep(1000);
//      					}
//     					
//     				} catch (Exception e) {
//     					Log.e(TAG, e.getMessage(), e);
//     				}
//     				//bckgrCacheThread = null;
//     			}
//     		});
//    	}
//    	
//    	if (!bckgrCacheThread.isAlive())bckgrCacheThread.start();
//    	
//    	
//		
//	}

    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if (mTmpFile != null) {
    		mTmpFile.delete();
    		mTmpFile = null;
    	}
    }
    
    
    
    
    
   // private static final String TAG = "Touch" ;
 // These matrices will be used to move and zoom image

    

}