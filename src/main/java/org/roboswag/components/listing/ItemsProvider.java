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

import org.roboswag.core.utils.android.RxAndroidUtils;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Scheduler;
import rx.internal.util.RxRingBuffer;
import rx.subjects.PublishSubject;

/**
 * Created by Gavriil Sitnikov on 07/12/2015.
 * TODO: fill description
 */
public abstract class ItemsProvider<T> {

    private final PublishSubject<List<ListChange>> listChangesSubject = PublishSubject.create();
    private final Scheduler scheduler = RxAndroidUtils.createLooperScheduler();

    @NonNull
    public Scheduler getScheduler() {
        return scheduler;
    }

    protected void notifyChanges(@NonNull final List<ListChange> listChanges) {
        scheduler.createWorker().schedule(() -> listChangesSubject.onNext(listChanges));
    }

    @Nullable
    public abstract T getItem(int position);

    public abstract Observable<T> loadItem(int position);

    public abstract int getSize();

    @SuppressWarnings("unchecked")
    public Observable<List<T>> loadRange(int first, int last) {
        final List<Observable<List<T>>> itemsRequests = new ArrayList<>();

        int i = first;
        while (i <= last) {
            final List<Observable<T>> limitedPageRequests = new ArrayList<>();
            final int maxIndex = i + RxRingBuffer.SIZE - 1;
            while (i <= Math.min(last, maxIndex)) {
                limitedPageRequests.add(loadItem(i));
                i++;
            }
            itemsRequests.add(Observable.combineLatest(limitedPageRequests, args -> {
                final List<T> resultPart = new ArrayList<>(args.length);
                for (final Object item : args) {
                    resultPart.add((T) item);
                }
                return resultPart;
            }));
        }

        return Observable.combineLatest(itemsRequests, args -> {
            final List<T> result = new ArrayList<>();
            for (final Object resultPart : args) {
                result.addAll((List<T>) resultPart);
            }
            return result;
        });
    }

    @NonNull
    public Observable<List<ListChange>> observeListChanges() {
        return listChangesSubject;
    }

    public static class ListChange {

        @NonNull
        private final Type type;
        private final int start;
        private final int count;

        public ListChange(final @NonNull Type type, final int start, final int count) {
            this.type = type;
            this.start = start;
            this.count = count;
        }

        @NonNull
        public Type getType() {
            return type;
        }

        public int getStart() {
            return start;
        }

        public int getCount() {
            return count;
        }

        public enum Type {
            INSERTED,
            CHANGED,
            REMOVED
        }

    }

}
