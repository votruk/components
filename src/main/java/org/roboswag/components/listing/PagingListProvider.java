/*
 *  Copyright (c) 2015 RoboSwag (Gavriil Sitnikov, Vsevolod Ivanov)
 *
 *  This file is part of RoboSwag library.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.roboswag.components.listing;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import org.roboswag.core.log.Lc;
import org.roboswag.core.utils.ShouldNotHappenException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import rx.Observable;
import rx.functions.Function;
import rx.schedulers.Schedulers;

/**
 * Created by Gavriil Sitnikov on 07/12/2015.
 * TODO: fill description
 */
public class PagingListProvider<T> implements ItemsProvider<T> {

    private static final int PAGE_SIZE = 25;

    private final SparseArray<List<T>> loadedPages = new SparseArray<>();
    private boolean isInitialized;
    @Nullable
    private Integer maxLoadedPage;
    @NonNull
    private final PageRequestCreator<T> pageRequestCreator;
    private boolean isLastPageLoaded;
    @Nullable
    private Integer totalCount;

    public PagingListProvider(@NonNull final PageRequestCreator<T> pageRequestCreator) {
        this.pageRequestCreator = pageRequestCreator;
    }

    @Nullable
    @Override
    public T getItem(final int position) {
        final List<T> page = loadedPages.get(position / PAGE_SIZE);
        return page != null ? page.get(position % PAGE_SIZE) : null;
    }

    @Override
    public int getSize() {
        return isInitialized && maxLoadedPage != null
                ? maxLoadedPage * PAGE_SIZE + loadedPages.get(maxLoadedPage).size() + (isLastPageLoaded ? 0 : 1)
                : 0;
    }

    public Observable<Integer> initialize() {
        isInitialized = false;
        return initialize(0);
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public Observable<Integer> initialize(final int loadToPosition) {
        if (isInitialized) {
            return Observable.just(isLastPageLoaded ? getSize() : getSize() - 1);
        }
        totalCount = null;
        loadedPages.clear();
        maxLoadedPage = null;
        isLastPageLoaded = false;

        final int itemsToLoad = loadToPosition / PAGE_SIZE + PAGE_SIZE;
        return pageRequestCreator.call(0, itemsToLoad)
                .first()
                .subscribeOn(Schedulers.io())
                .map(page -> {
                    isInitialized = true;
                    totalCount = page.getTotalCount();
                    final Iterator<T> iterator = page.getItems().iterator();
                    int index = 0;
                    ArrayList<T> pageToAdd = new ArrayList<>(PAGE_SIZE);
                    while (iterator.hasNext()) {
                        pageToAdd.add(iterator.next());
                        index++;
                        if (index % PAGE_SIZE == 0) {
                            loadedPages.put((index - 1) / PAGE_SIZE, pageToAdd);
                            pageToAdd = new ArrayList<>(PAGE_SIZE);
                        }
                    }

                    maxLoadedPage = (index - 1) / PAGE_SIZE;
                    if (!pageToAdd.isEmpty()) {
                        loadedPages.put(maxLoadedPage, pageToAdd);
                        isLastPageLoaded = pageToAdd.size() < PAGE_SIZE;
                    } else {
                        maxLoadedPage--;
                        isLastPageLoaded = true;
                    }
                    return isLastPageLoaded ? getSize() : getSize() - 1;
                });
    }

    //TODO: nearest pages + preloading + observable for items collection changes
    @Override
    public Observable loadItem(final int position) {
        if (!isInitialized) {
            Lc.fatalException(new ShouldNotHappenException("Provider should be initialized first"));
            return Observable.empty();
        }
        return loadPage(position / PAGE_SIZE);
    }

    private Observable loadPage(final int index) {
        final List<T> loadedPage = loadedPages.get(index);
        if (loadedPage != null) {
            return Observable.just(loadedPage);
        } else {
            return pageRequestCreator.call(index * PAGE_SIZE, PAGE_SIZE)
                    .first()
                    .subscribeOn(Schedulers.io())
                    .map(page -> onPageLoaded(index, page));
        }
    }

    //TODO: if something loaded or if loaded emty with index=999
    private Collection<T> onPageLoaded(final int index, @NonNull final Page<T> page) {
        if (maxLoadedPage == null || maxLoadedPage != index - 1) {
            throw new ShouldNotHappenException("Loaded page index is illegal: " + index + " but not " + maxLoadedPage + 1);
        }

        final List<T> pageItems = page.getItems().size() <= PAGE_SIZE
                ? new ArrayList<>(page.getItems())
                : new ArrayList<>(page.getItems()).subList(0, PAGE_SIZE);
        maxLoadedPage++;
        loadedPages.put(index, pageItems);
        if (pageItems.size() < PAGE_SIZE
                || (maxLoadedPage != null && totalCount != null && maxLoadedPage * PAGE_SIZE + pageItems.size() >= totalCount)) {
            isLastPageLoaded = true;
        }
        return pageItems;
    }

    public interface PageRequestCreator<T> extends Function {

        @NonNull
        Observable<Page<T>> call(int offset, int limit);

    }

}