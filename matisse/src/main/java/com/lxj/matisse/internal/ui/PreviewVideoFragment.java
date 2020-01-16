package com.lxj.matisse.internal.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.lxj.matisse.R;
import com.lxj.matisse.internal.entity.Item;
import com.lxj.matisse.internal.entity.SelectionSpec;
import com.lxj.matisse.internal.utils.PathUtils;
import com.lxj.matisse.internal.utils.PhotoMetadataUtils;
import com.lxj.matisse.listener.OnFragmentInteractionListener;

public class PreviewVideoFragment extends Fragment implements MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    private static final String ARGS_ITEM = "args_item";
    private VideoView vvPreviewVideo;
    private ImageView ivPlay;
    private ImageView ivThumbnail;

    private MediaController mMediaController;
    private Item videoData;

    public static PreviewVideoFragment newInstance(Item item) {
        PreviewVideoFragment fragment = new PreviewVideoFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ARGS_ITEM, item);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preview_video, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vvPreviewVideo = view.findViewById(R.id.vvPreviewVideo);
        ivPlay = view.findViewById(R.id.ivPlay);
        ivThumbnail = view.findViewById(R.id.ivThumbnail);
        mMediaController = new MediaController(getContext());
        videoData = getArguments().getParcelable(ARGS_ITEM);
        if (videoData == null) {
            return;
        }
        vvPreviewVideo.setOnCompletionListener(this);
        vvPreviewVideo.setOnPreparedListener(this);
        vvPreviewVideo.setOnErrorListener(this);
        vvPreviewVideo.setMediaController(mMediaController);
        ivPlay.setOnClickListener(v -> {
            if (vvPreviewVideo.getCurrentPosition() > 0)
                vvPreviewVideo.start();
            else {
                vvPreviewVideo.setVideoPath(PathUtils.getPath(getContext(), videoData.getContentUri()));
                vvPreviewVideo.start();
            }
            ivPlay.setVisibility(View.INVISIBLE);
        });
        Point size = PhotoMetadataUtils.getBitmapSize(videoData.getContentUri(), getActivity());
        SelectionSpec.getInstance().imageEngine.loadImage(getContext(), size.x, size.y, ivThumbnail,
                videoData.getContentUri());
    }

    @Override
    public void onStop() {
        super.onStop();
        vvPreviewVideo.pause();
        ivPlay.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroy() {
        mMediaController = null;
        vvPreviewVideo = null;
        ivPlay = null;
        super.onDestroy();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (null != ivPlay) {
            ivPlay.setVisibility(View.VISIBLE);
            ivThumbnail.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        onCompletion(mp);
        return true;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.setOnInfoListener((mp1, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                vvPreviewVideo.setBackgroundColor(Color.TRANSPARENT);
                ivThumbnail.setVisibility(View.GONE);
                return true;
            }
            return false;
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
