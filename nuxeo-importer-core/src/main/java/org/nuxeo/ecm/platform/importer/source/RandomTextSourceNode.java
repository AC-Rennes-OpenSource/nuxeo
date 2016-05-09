/*
 * (C) Copyright 2006-2008 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.importer.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.platform.importer.random.DictionaryHolder;
import org.nuxeo.ecm.platform.importer.random.HunspellDictionaryHolder;
import org.nuxeo.ecm.platform.importer.random.RandomTextGenerator;

/**
 * Random {@link SourceNode} to be used for load testing
 *
 * @author Thierry Delprat
 */
public class RandomTextSourceNode implements SourceNode {

    protected static RandomTextGenerator gen;

    protected static int maxNode = 10000;

    public static int maxDepth = 8;

    public static int defaultNbDataNodesPerFolder = 100;

    protected static int minGlobalFolders = 0;

    protected static int minFoldersPerNode = 0;

    protected static Integer nbNodes = 1;

    protected static Integer nbFolders = 0;

    protected static Long size;

    protected Random hazard;

    protected String name;

    protected boolean folderish;

    protected int level = 0;

    protected int idx = 0;

    protected static Integer blobSizeInKB;

    protected List<SourceNode> cachedChildren = null;

    public static boolean CACHE_CHILDREN = false;

    protected boolean onlyText = true;

    public RandomTextSourceNode(boolean folderish, int level, int idx, boolean onlyText) {
        this.folderish = folderish;
        hazard = new Random();
        this.level = level;
        this.idx = idx;
        this.onlyText = onlyText;
    }

    public static RandomTextSourceNode init(int maxSize) {
        return init(maxSize, null, true);
    }

    public static RandomTextSourceNode init(int maxSize, Integer blobSizeInKB, boolean onlyText) {
        return init(maxSize, blobSizeInKB, onlyText, new HunspellDictionaryHolder("fr_FR.dic"));
    }

    public static RandomTextSourceNode init(int maxSize, Integer blobSizeInKB, boolean onlyText,
            DictionaryHolder dictionaryHolder) {
        gen = new RandomTextGenerator(dictionaryHolder);
        gen.prefilCache();
        maxNode = maxSize;
        nbNodes = 1;
        size = new Long(0);
        RandomTextSourceNode.blobSizeInKB = blobSizeInKB;
        minGlobalFolders = maxNode / defaultNbDataNodesPerFolder;
        minFoldersPerNode = 1 + (int) Math.pow(minGlobalFolders, (1.0 / maxDepth));
        return new RandomTextSourceNode(true, 0, 0, onlyText);
    }

    protected String getBlobMimeType() {
        if (onlyText) {
            return "text/plain";
        } else {
            return "text/partial";
        }
    }

    public BlobHolder getBlobHolder() {
        if (folderish) {
            return null;
        }
        String content = null;

        if (blobSizeInKB == null) {
            content = gen.getRandomText();
        } else {
            content = gen.getRandomText(blobSizeInKB);
        }
        synchronized (size) {
            size += content.length();
        }
        Blob blob = Blobs.createBlob(content, getBlobMimeType(), null, getName() + ".txt");
        return new SimpleBlobHolder(blob);
    }

    protected int getMidRandom(int target) {
        return 1 + (target / 2) + hazard.nextInt(target);
    }

    protected int getMaxChildren() {
        if (maxNode < nbNodes) {
            return 0;
        }
        int targetRemainingFolders = minGlobalFolders - nbFolders;
        if (targetRemainingFolders <= 0) {
            return defaultNbDataNodesPerFolder + 1;
        }
        int target = ((maxNode - nbNodes) / targetRemainingFolders);
        if (target <= 0) {
            return 0;
        }
        return getMidRandom(target);
    }

    protected int getMaxFolderish() {
        if (maxNode <= nbNodes) {
            return 0;
        }
        return getMidRandom(minFoldersPerNode);
    }

    @Override
    public List<SourceNode> getChildren() {

        if (!folderish) {
            return null;
        }

        if (cachedChildren != null) {
            return cachedChildren;
        }

        List<SourceNode> children = new ArrayList<SourceNode>();
        if (nbNodes > maxNode) {
            return children;
        }

        int nbChildren = getMaxChildren();

        synchronized (nbNodes) {
            nbNodes = nbNodes + nbChildren;
        }
        for (int i = 0; i < nbChildren; i++) {
            children.add(new RandomTextSourceNode(false, level, i, onlyText));
        }
        if (level < maxDepth) {
            int nbFolderish = getMaxFolderish();
            for (int i = 0; i < nbFolderish; i++) {
                children.add(new RandomTextSourceNode(true, level + 1, i, onlyText));
            }
            synchronized (nbFolders) {
                nbFolders = nbFolders + nbFolderish;
            }
        }
        if (CACHE_CHILDREN) {
            cachedChildren = children;
        }
        return children;
    }

    @Override
    public String getName() {
        if (name == null) {
            if (folderish) {
                name = "folder";
            } else {
                name = "file";
            }
            if (level == 0 && folderish) {
                name = name + "-" + (System.currentTimeMillis() % 10000) + hazard.nextInt(100);
            } else {
                name = name + "-" + level + "-" + idx;
            }
        }
        return name;
    }

    @Override
    public boolean isFolderish() {
        return folderish;
    }

    public static Integer getNbNodes() {
        return nbNodes;
    }

    public static Long getSize() {
        return size;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public String getSourcePath() {
        return null;
    }
}
