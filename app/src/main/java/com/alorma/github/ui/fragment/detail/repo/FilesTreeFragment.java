package com.alorma.github.ui.fragment.detail.repo;

import android.app.ListFragment;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.alorma.github.R;
import com.alorma.github.sdk.bean.dto.response.Content;
import com.alorma.github.sdk.bean.dto.response.ContentType;
import com.alorma.github.sdk.bean.dto.response.ListContents;
import com.alorma.github.sdk.bean.dto.response.UpContent;
import com.alorma.github.sdk.services.client.BaseClient;
import com.alorma.github.sdk.services.repo.GetRepoContentsClient;
import com.alorma.github.ui.activity.FileActivity;
import com.alorma.github.ui.adapter.detail.repo.RepoContentAdapter;
import com.alorma.github.ui.listeners.RefreshListener;
import com.bugsense.trace.BugSenseHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by Bernat on 20/07/2014.
 */
public class FilesTreeFragment extends ListFragment implements BaseClient.OnResultCallback<ListContents> {

    public static final String OWNER = "OWNER";
    public static final String REPO = "REPO";
    private String owner;
    private String repo;
    private RepoContentAdapter contentAdapter;
    private RefreshListener refreshListener;
    private Map<Content, ListContents> treeContent;
    private Content rootContent = new Content();
    private Content currentSelectedContent = rootContent;

    public static FilesTreeFragment newInstance(String owner, String repo, RefreshListener refreshListener) {
        Bundle bundle = new Bundle();
        bundle.putString(OWNER, owner);
        bundle.putString(REPO, repo);

        FilesTreeFragment f = new FilesTreeFragment();
        f.setRefreshListener(refreshListener);
        f.setArguments(bundle);
        return f;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setBackgroundColor(getResources().getColor(R.color.gray_github));

        if (getArguments() != null) {
            owner = getArguments().getString(OWNER);
            repo = getArguments().getString(REPO);

            GetRepoContentsClient repoContentsClient = new GetRepoContentsClient(getActivity(), owner, repo);
            repoContentsClient.setOnResultCallback(this);
            repoContentsClient.execute();

            treeContent = new HashMap<Content, ListContents>();
            treeContent.put(currentSelectedContent, null);

            if (refreshListener != null) {
                refreshListener.showRefresh();
            }
        }
    }

    @Override
    public void onResponseOk(ListContents contents, Response r) {

        displayContent(contents);
    }

    private void displayContent(ListContents contents) {
        if (contents != null) {
            try {
                ListContents currentContents = treeContent.get(currentSelectedContent);

                if (currentContents == null) {
                    int size = contents.size() + (currentSelectedContent.parent != null ? 1 : 0);
                    currentContents = new ListContents(size);
                    treeContent.put(currentSelectedContent, currentContents);
                    if (currentSelectedContent.parent != null) {
                        Content up = new UpContent();
                        up.parent = currentSelectedContent.parent;
                        currentContents.add(up);
                    }

                    Collections.sort(contents, ListContents.SORT.TYPE);
                    currentContents.addAll(contents);

                    contentAdapter = new RepoContentAdapter(getActivity(), currentContents);
                } else {
                    contentAdapter = new RepoContentAdapter(getActivity(), contents);
                }

                setListAdapter(contentAdapter);

                if (refreshListener != null) {
                    refreshListener.cancelRefresh();
                }
            } catch (Exception e) {
                BugSenseHandler.addCrashExtraData("FilesTreeFragment", e.getMessage());
                BugSenseHandler.flush(getActivity());
            }
        }
    }

    @Override
    public void onFail(RetrofitError error) {
        Log.e("FILES", "Error", error);
        if (refreshListener != null) {
            refreshListener.cancelRefresh();
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        if (contentAdapter != null && contentAdapter.getCount() >= position) {
            Content item = contentAdapter.getItem(position);
            if (item.isDir()) {
                if (refreshListener != null) {
                    refreshListener.showRefresh();
                }

                if (treeContent.get(item) == null) {
                    item.parent = currentSelectedContent;

                    currentSelectedContent = item;
                    treeContent.put(item, null);

                    GetRepoContentsClient repoContentsClient = new GetRepoContentsClient(getActivity(), owner, repo, item.path);
                    repoContentsClient.setOnResultCallback(this);
                    repoContentsClient.execute();
                } else {
                    displayContent(treeContent.get(item));
                }

            } else if (item.isFile()) {
                String url = item._links.html;
                Intent intent = FileActivity.createLauncherIntent(getActivity(), url);
                startActivity(intent);
            } else if (ContentType.up.equals(item.type)) {
                if (item.parent != null) {
                    currentSelectedContent = item.parent;
                    displayContent(treeContent.get(currentSelectedContent));
                }
            }
        }
    }

    public void setRefreshListener(RefreshListener refreshListener) {
        this.refreshListener = refreshListener;
    }
}