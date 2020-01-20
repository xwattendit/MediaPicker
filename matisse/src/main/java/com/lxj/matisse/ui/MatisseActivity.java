/*
 * Copyright 2017 Zhihu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lxj.matisse.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.gyf.barlibrary.ImmersionBar;
import com.lxj.matisse.CaptureMode;
import com.lxj.matisse.MatisseConst;
import com.lxj.matisse.MimeType;
import com.lxj.matisse.R;
import com.lxj.matisse.internal.entity.Album;
import com.lxj.matisse.internal.entity.Item;
import com.lxj.matisse.internal.entity.SelectionSpec;
import com.lxj.matisse.internal.entity.UcropSize;
import com.lxj.matisse.internal.model.AlbumCollection;
import com.lxj.matisse.internal.model.SelectedItemCollection;
import com.lxj.matisse.internal.ui.AlbumPreviewActivity;
import com.lxj.matisse.internal.ui.BasePreviewActivity;
import com.lxj.matisse.internal.ui.MediaSelectionFragment;
import com.lxj.matisse.internal.ui.SelectedPreviewActivity;
import com.lxj.matisse.internal.ui.adapter.AlbumMediaAdapter;
import com.lxj.matisse.internal.ui.adapter.AlbumsAdapter;
import com.lxj.matisse.internal.ui.widget.AlbumPopup;
import com.lxj.matisse.internal.utils.MediaStoreCompat;
import com.lxj.matisse.internal.utils.PathUtils;
import com.lxj.matisse.internal.utils.PhotoMetadataUtils;
import com.lxj.xpermission.PermissionConstants;
import com.lxj.xpermission.XPermission;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.ArrayList;

/**
 * Main Activity to display albums and media content (images/videos) in each album
 * and also support media selecting operations.
 */
public class MatisseActivity extends AppCompatActivity implements
        AlbumCollection.AlbumCallbacks, AdapterView.OnItemClickListener,
        MediaSelectionFragment.SelectionProvider, View.OnClickListener,
        AlbumMediaAdapter.CheckStateListener, AlbumMediaAdapter.OnMediaClickListener,
        AlbumMediaAdapter.OnPhotoCapture {

    public static final String CHECK_STATE = "checkState";
    private final AlbumCollection mAlbumCollection = new AlbumCollection();
    private MediaStoreCompat mMediaStoreCompat;
    private SelectedItemCollection mSelectedCollection = new SelectedItemCollection(this);
    private SelectionSpec mSpec;

    private AlbumsAdapter mAlbumsAdapter;
    private AlbumPopup albumPopup;
    private Button mButtonPreview;
    private Button mButtonApply;
    private Button mButtonClear;
    private TextView mSelected;
    private View mContainer;
    private View mEmptyView;

    private RelativeLayout toolbar;
    private ImageView ivBack;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // programmatically set theme before super.onCreate()
        mSpec = SelectionSpec.getInstance();
        super.onCreate(savedInstanceState);
        if (!mSpec.hasInited) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        setContentView(R.layout.activity_matisse);

        if (mSpec.needOrientationRestriction()) {
            setRequestedOrientation(mSpec.orientation);
        }

        if (mSpec.capture) {
            mMediaStoreCompat = new MediaStoreCompat(this);
            if (mSpec.captureStrategy == null)
                throw new RuntimeException("Don't forget to set CaptureStrategy.");
            mMediaStoreCompat.setCaptureStrategy(mSpec.captureStrategy);
        }

        toolbar = findViewById(R.id.toolbar);
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            int stateBarHeight = getResources().getDimensionPixelSize(resourceId);
            final float scale = getResources().getDisplayMetrics().density;
            int height =  (int) (44 * scale + 0.5f);
            toolbar.getLayoutParams().height = stateBarHeight + height;
        }
        ivBack = findViewById(R.id.ivBack);
        mButtonPreview = findViewById(R.id.button_preview);
        mButtonApply = findViewById(R.id.button_apply);
        mButtonClear = findViewById(R.id.button_clear);
        mSelected = findViewById(R.id.selected_album);
        mContainer = findViewById(R.id.container);
        mEmptyView = findViewById(R.id.empty_view);

        ivBack.setOnClickListener(this);
        mButtonPreview.setOnClickListener(this);
        mSelected.setOnClickListener(this);
        mButtonApply.setOnClickListener(this);
        mButtonClear.setOnClickListener(this);
        if (mSpec.selected != null) {
            if (savedInstanceState == null)
                savedInstanceState = new Bundle();
            ArrayList<Item> value = new ArrayList<>(mSpec.selected);
            savedInstanceState.putParcelableArrayList(SelectedItemCollection.STATE_SELECTION, value);
            if (value.get(0) != null) {
                boolean image = MimeType.isImage(value.get(0).mimeType);
                if (image)
                    savedInstanceState.putInt(SelectedItemCollection.STATE_COLLECTION_TYPE,SelectedItemCollection.COLLECTION_IMAGE);
                else
                    savedInstanceState.putInt(SelectedItemCollection.STATE_COLLECTION_TYPE,SelectedItemCollection.COLLECTION_VIDEO);
            }
        }
        mSelectedCollection.onCreate(savedInstanceState);
        mButtonClear.setEnabled(mSelectedCollection.asList().size() > 0);
        updateBottomToolbar();

        mAlbumsAdapter = new AlbumsAdapter(this, null, false);
        albumPopup = new AlbumPopup(this, mAlbumsAdapter, this);
        mAlbumCollection.onCreate(this, this);
        mAlbumCollection.onRestoreInstanceState(savedInstanceState);
        mAlbumCollection.loadAlbums();
        ImmersionBar.with(this).statusBarDarkFont(true,0.1f).init();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mSelectedCollection.onSaveInstanceState(outState);
        mAlbumCollection.onSaveInstanceState(outState);
        outState.putBoolean("checkState", true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAlbumCollection.onDestroy();
        mSpec.onCheckedListener = null;
        mSpec.onSelectedListener = null;
        ImmersionBar.with(this).destroy();
        mSpec.selected = null;
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }

    ArrayList<Uri> selectedUris = new ArrayList<>();
    ArrayList<String> selectedPaths = new ArrayList<>();

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;
        if (requestCode == MatisseConst.REQUEST_CODE_PREVIEW) {
            Bundle resultBundle = data.getBundleExtra(BasePreviewActivity.EXTRA_RESULT_BUNDLE);
            ArrayList<Item> selected = resultBundle.getParcelableArrayList(SelectedItemCollection.STATE_SELECTION);
            int collectionType = resultBundle.getInt(SelectedItemCollection.STATE_COLLECTION_TYPE,
                    SelectedItemCollection.COLLECTION_UNDEFINED);
            if (data.getBooleanExtra(BasePreviewActivity.EXTRA_RESULT_APPLY, false)) {
                Intent result = new Intent();
                selectedUris.clear();
                selectedPaths.clear();
                if (selected != null) {
                    for (Item item : selected) {
                        selectedUris.add(item.getContentUri());
                        selectedPaths.add(PathUtils.getPath(this, item.getContentUri()));
                    }
                }
                result.putParcelableArrayListExtra(MatisseConst.EXTRA_RESULT_SELECTION, selected);
                result.putStringArrayListExtra(MatisseConst.EXTRA_RESULT_SELECTION_PATH, selectedPaths);
                result.putExtra(MatisseConst.EXTRA_RESULT_ORIGINAL_ENABLE, true);
                if (mSpec.isCrop && selected != null && selected.size() == 1 && selected.get(0).isImage()) {
                    //开启裁剪
                    startCrop(this, selected.get(0).uri,mSpec.size);
                    return;
                }
                setResult(RESULT_OK, result);
                finish();
            } else {
                mSelectedCollection.overwrite(selected, collectionType);
                Fragment mediaSelectionFragment = getSupportFragmentManager().findFragmentByTag(
                        MediaSelectionFragment.class.getSimpleName());
                if (mediaSelectionFragment instanceof MediaSelectionFragment) {
                    ((MediaSelectionFragment) mediaSelectionFragment).refreshMediaGrid();
                }
                updateBottomToolbar();
            }
        } else if (requestCode == MatisseConst.REQUEST_CODE_CAPTURE) {
            // Just pass the data back to previous calling Activity.
            setResult(RESULT_OK, data);
            finish();
        } else if (requestCode == UCrop.REQUEST_CROP) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                //finish with result.
                Intent result = getIntent();
                result.putExtra(MatisseConst.EXTRA_RESULT_CROP_PATH, resultUri.getPath());
//                ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
                result.putParcelableArrayListExtra(MatisseConst.EXTRA_RESULT_SELECTION, mSelectedCollection.asList());
                ArrayList<String> selectedPaths = (ArrayList<String>) mSelectedCollection.asListOfString();
                result.putStringArrayListExtra(MatisseConst.EXTRA_RESULT_SELECTION_PATH, selectedPaths);
                setResult(RESULT_OK, result);
                finish();
            } else {
                Log.e("Matisse", "ucrop occur error: " + UCrop.getError(data).toString());
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            Log.e("Matisse", "ucrop occur error: " + UCrop.getError(data).toString());
        }
    }

    private void updateBottomToolbar() {

        int selectedCount = mSelectedCollection.count();
        if (selectedCount == 0) {
            mButtonPreview.setEnabled(false);
            mButtonApply.setEnabled(false);
            mButtonApply.setText(getString(R.string.button_sure_default));
        } else if (selectedCount == 1 && mSpec.singleSelectionModeEnabled()) {
            mButtonPreview.setEnabled(true);
            mButtonApply.setText(R.string.button_sure_default);
            mButtonApply.setEnabled(true);
        } else {
            mButtonPreview.setEnabled(true);
            mButtonApply.setEnabled(true);
            mButtonApply.setText(getString(R.string.button_sure, selectedCount,mSelectedCollection.currentMaxSelectable()));
        }
    }


    private int countOverMaxSize() {
        int count = 0;
        int selectedCount = mSelectedCollection.count();
        for (int i = 0; i < selectedCount; i++) {
            Item item = mSelectedCollection.asList().get(i);

            if (item.isImage()) {
                float size = PhotoMetadataUtils.getSizeInMB(item.size);
                if (size > mSpec.originalMaxSize) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_preview) {
            Intent intent = new Intent(this, SelectedPreviewActivity.class);
            intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
            intent.putExtra(BasePreviewActivity.EXTRA_RESULT_ORIGINAL_ENABLE, true);
            startActivityForResult(intent, MatisseConst.REQUEST_CODE_PREVIEW);
        } else if (v.getId() == R.id.button_apply) {
            Intent result = new Intent();
            ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
            result.putParcelableArrayListExtra(MatisseConst.EXTRA_RESULT_SELECTION, mSelectedCollection.asList());
            ArrayList<String> selectedPaths = (ArrayList<String>) mSelectedCollection.asListOfString();
            result.putStringArrayListExtra(MatisseConst.EXTRA_RESULT_SELECTION_PATH, selectedPaths);
            result.putExtra(MatisseConst.EXTRA_RESULT_ORIGINAL_ENABLE, true);
            if (mSpec.isCrop && selectedPaths.size() == 1 && mSelectedCollection.asList().get(0).isImage()) {
                //start crop
                startCrop(this, selectedUris.get(0),mSpec.size);
            } else {
                setResult(RESULT_OK, result);
                finish();
            }
        } else if (v.getId() == R.id.selected_album) {
            //选择相册
            if (albumPopup.isShow())
                albumPopup.dismiss();
            else
                albumPopup.show();
        } else if (v.getId() == R.id.ivBack){
            onBackPressed();
        } else if (v.getId() == R.id.button_clear){
            mSelectedCollection.overwrite(new ArrayList<>(),SelectedItemCollection.COLLECTION_UNDEFINED);
            Fragment mediaSelectionFragment = getSupportFragmentManager().findFragmentByTag(
                    MediaSelectionFragment.class.getSimpleName());
            if (mediaSelectionFragment instanceof MediaSelectionFragment) {
                ((MediaSelectionFragment) mediaSelectionFragment).refreshMediaGrid();
            }
            updateBottomToolbar();
        }
    }

    public static void startCrop(AppCompatActivity context, Uri source, UcropSize size) {
        String destinationFileName = System.nanoTime() + "_crop.jpg";
        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(90);
        options.setToolbarCancelDrawable(R.mipmap.public_back);
        options.setToolbarWidgetColor(Color.WHITE);
        options.setActiveWidgetColor(Color.WHITE);
        options.setStatusBarColor(Color.BLACK);
        options.setToolbarColor(Color.BLACK);
        options.setDimmedLayerColor(Color.BLACK);
        options.setCropFrameColor(Color.parseColor("#77EEEEEE"));
        options.setCropGridColor(Color.parseColor("#77EEEEEE"));
        options.setRootViewBackgroundColor(Color.BLACK);
        options.setShowCropFrame(true);
        options.setShowCropGrid(true);
        options.setHideBottomControls(true);
        if (size != null) {
            options.withMaxResultSize(size.getWidth(), size.getHeight());
            options.withAspectRatio(size.getWidth(),size.getHeight());
        }
        File cacheFile = new File(context.getCacheDir(), destinationFileName);
        UCrop.of(source, Uri.fromFile(cacheFile))
                .withAspectRatio(1, 1)
                .withOptions(options)
                .start(context);
    }

    //相册条目点击
    @Override
    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
        albumPopup.dismiss();
        new Handler().postDelayed(() -> {
            mAlbumCollection.setStateCurrentSelection(position);
            mAlbumsAdapter.getCursor().moveToPosition(position);
            Album album = Album.valueOf(mAlbumsAdapter.getCursor());
            if (album.isAll() && SelectionSpec.getInstance().capture) {
                album.addCaptureCount();
            }
            onAlbumSelected(album);
        }, 200);
    }

    @Override
    public void onAlbumLoad(final Cursor cursor) {
        mAlbumsAdapter.swapCursor(cursor);
        // select default album.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                cursor.moveToPosition(mAlbumCollection.getCurrentSelection());
                Album album = Album.valueOf(cursor);
                if (album.isAll() && SelectionSpec.getInstance().capture) {
                    album.addCaptureCount();
                }
                onAlbumSelected(album);
            }
        });
    }

    @Override
    public void onAlbumReset() {
        mAlbumsAdapter.swapCursor(null);
    }

    private void onAlbumSelected(Album album) {
        mSelected.setText(album.getDisplayName(this));
        mAlbumsAdapter.updateSelection(album.getId());
        if (album.isAll() && album.isEmpty()) {
            mContainer.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            Fragment fragment = MediaSelectionFragment.newInstance(album);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment, MediaSelectionFragment.class.getSimpleName())
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onUpdate() {
        // notify bottom toolbar that check state changed.
        updateBottomToolbar();
        mButtonClear.setEnabled(mSelectedCollection.asList().size() > 0);
        if (mSpec.onSelectedListener != null) {
            mSpec.onSelectedListener.onSelected(mSelectedCollection.asListOfUri(), mSelectedCollection.asListOfString());
        }
    }

    @Override
    public void onMediaClick(Album album, Item item, int adapterPosition) {
        Intent intent = new Intent(this, AlbumPreviewActivity.class);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ALBUM, album);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ITEM, item);
        intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
        intent.putExtra(BasePreviewActivity.EXTRA_RESULT_ORIGINAL_ENABLE, true);
        startActivityForResult(intent, MatisseConst.REQUEST_CODE_PREVIEW);
    }

    @Override
    public SelectedItemCollection provideSelectedItemCollection() {
        return mSelectedCollection;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void capture() {
//        if (mMediaStoreCompat != null) {
//            mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE);
//        }


        String[] permissions = mSpec.captureMode == CaptureMode.Image ? new String[]{PermissionConstants.CAMERA}
                : new String[]{PermissionConstants.CAMERA, PermissionConstants.MICROPHONE};

        XPermission.create(this, permissions).callback(new XPermission.SimpleCallback() {
            @Override
            public void onGranted() {
                Intent intent = new Intent(MatisseActivity.this, CameraActivity.class);
                startActivityForResult(intent, MatisseConst.REQUEST_CODE_CAPTURE);
            }

            @Override
            public void onDenied() {
                Toast.makeText(MatisseActivity.this, "没有权限，无法使用该功能", Toast.LENGTH_SHORT).show();
            }
        }).request();
    }


}
