package com.example.clipview;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.content.res.Configuration;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private TextView textView;
    private ImageView imageView;
    private ScrollView scrollView;
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipChangedListener;
    private ImageButton lockButton;

    private Matrix matrix;
    private float scale = 1f;
    private float translateX = 0f;
    private float translateY = 0f;
    private PointF lastTouch = new PointF();
    private ScaleGestureDetector scaleGestureDetector;

    private boolean isMovingImage = false; // Track if the image is currently moving
    private boolean isZooming = false; // Track if the image is zooming
    private boolean isLocked = false; // Track lock state

    // Added for state saving
    private String savedText = null;
    private Uri savedImageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        // Prevent the screen from sleeping
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        textView = findViewById(R.id.textView);
        imageView = findViewById(R.id.imageView);
        scrollView = findViewById(R.id.scrollView);
        lockButton = findViewById(R.id.lockButton);

        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipChangedListener = this::pasteFromClipboard;
        clipboardManager.addPrimaryClipChangedListener(clipChangedListener);

        matrix = new Matrix();
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        // Register the back press callback
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (imageView.getVisibility() == View.VISIBLE) {
                    resetImageView(); // Re-center the image
                } else {
                    setEnabled(false); // Disable this callback to allow default behavior
                    MainActivity.super.onBackPressed(); // Correctly call the super method
                }
            }
        };

        getOnBackPressedDispatcher().addCallback(this, callback);

        // Restore state if available
        if (savedInstanceState != null) {
            savedText = savedInstanceState.getString("savedText");
            savedImageUri = savedInstanceState.getParcelable("savedImageUri");
            restoreClipboardContent();
        }

        lockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocked) {
                    // Unlock: re-enable clipboard updates and show current clipboard image
                    clipboardManager.addPrimaryClipChangedListener(clipChangedListener);
                    lockButton.setImageResource(R.drawable.ic_unlock);
                    isLocked = false;
                    pasteFromClipboard(); // Show current clipboard image/text
                } else {
                    // Lock: freeze current screen and disable clipboard updates
                    clipboardManager.removePrimaryClipChangedListener(clipChangedListener);
                    lockButton.setImageResource(R.drawable.ic_lock);
                    isLocked = true;
                }
            }
        });
    }

    private void pasteFromClipboard() {
        ClipData clipData = clipboardManager.getPrimaryClip();

        if (clipData != null && clipData.getItemCount() > 0) {
            ClipData.Item item = clipData.getItemAt(0);
            if (item.getText() != null) {
                savedText = item.getText().toString(); // Save the text for restoring
                textView.setText(savedText);
                textView.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.GONE);
                scrollView.setVisibility(View.VISIBLE);
            } else if (item.getUri() != null) {
                savedImageUri = item.getUri(); // Save the image URI for restoring
                try {
                    Bitmap bitmap = resizeImage(savedImageUri);
                    imageView.setImageBitmap(bitmap);
                    imageView.setVisibility(View.VISIBLE);
                    textView.setVisibility(View.GONE);
                    scrollView.setVisibility(View.GONE);
                    centerImageOnPaste(); // Center the image when displayed
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void restoreClipboardContent() {
        if (savedText != null) {
            textView.setText(savedText);
            textView.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
        } else if (savedImageUri != null) {
            try {
                Bitmap bitmap = resizeImage(savedImageUri);
                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(View.VISIBLE);
                textView.setVisibility(View.GONE);
                scrollView.setVisibility(View.GONE);
                centerImageOnPaste(); // Center the image when displayed
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Bitmap resizeImage(Uri imageUri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
        inputStream.close();

        // Get original dimensions
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();

        // Get the screen width and height
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        android.graphics.Point size = new android.graphics.Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        int screenWidth = size.x;
        int screenHeight = size.y;

        // Calculate the aspect ratio of the image
        float imageAspectRatio = (float) originalWidth / originalHeight;

        // Calculate the aspect ratio of the screen
        float screenAspectRatio = (float) screenWidth / screenHeight;

        // Determine new dimensions based on the aspect ratios
        int newWidth, newHeight;

        if (imageAspectRatio > screenAspectRatio) {
            // Image is wider relative to the screen, fit to screen width
            newWidth = screenWidth;
            newHeight = Math.round(screenWidth / imageAspectRatio);
        } else {
            // Image is taller relative to the screen, fit to screen height
            newHeight = screenHeight;
            newWidth = Math.round(screenHeight * imageAspectRatio);
        }

        return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
    }

    private void centerImageOnPaste() {
        // Use ViewTreeObserver to wait for the layout to be ready before centering
        imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                resetImageView(); // Center the image
                imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this); // Remove listener to avoid repeated calls
            }
        });
    }

    private void resetImageView() {
        // Center the image
        translateX = (imageView.getWidth() - imageView.getDrawable().getIntrinsicWidth()) / 2f;
        translateY = (imageView.getHeight() - imageView.getDrawable().getIntrinsicHeight()) / 2f;
        scale = 1f; // Reset scale to 1
        updateImageMatrix(); // Apply the transformation
    }

    private void updateImageMatrix() {
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(translateX, translateY);
        imageView.setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);

        // Get the number of fingers currently touching the screen
        int pointerCount = event.getPointerCount();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(0), event.getY(0));
                isMovingImage = true; // Start moving the image
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 2) {
                    // Allow both zooming and moving the image with two fingers
                    float dx = event.getX(0) - lastTouch.x; // Use first finger for movement
                    float dy = event.getY(0) - lastTouch.y;
                    translateX += dx;
                    translateY += dy;
                    lastTouch.set(event.getX(0), event.getY(0)); // Update last touch position
                    updateImageMatrix();
                    isZooming = true; // Set zooming flag when two fingers are in use
                } else if (pointerCount == 1 && isMovingImage) {
                    // Allow moving the image only with one finger
                    float dx = event.getX(0) - lastTouch.x; // Use first finger for movement
                    float dy = event.getY(0) - lastTouch.y;
                    translateX += dx;
                    translateY += dy;
                    lastTouch.set(event.getX(0), event.getY(0)); // Update last touch position
                    updateImageMatrix();
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                // If one finger is lifted in a two-finger gesture, stop all movements
                if (isZooming) {
                    isMovingImage = false; // Stop movement on pointer up
                }
                break;

            case MotionEvent.ACTION_UP:
                // Reset last touch and stop movement
                lastTouch.set(0, 0);
                isMovingImage = false; // Stop movement
                isZooming = false; // Reset zooming flag
                break;
        }

        return true;
    }

    // ScaleListener to handle zooming
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            scale *= scaleFactor;
            updateImageMatrix();
            return true;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("savedText", savedText);
        outState.putParcelable("savedImageUri", savedImageUri);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up listener if not locked
        if (!isLocked) {
            clipboardManager.removePrimaryClipChangedListener(clipChangedListener);
        }
    }
}
