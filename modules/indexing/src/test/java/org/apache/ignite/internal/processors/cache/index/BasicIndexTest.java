/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.index;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.QueryIndex;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.cache.IgniteInternalCache;
import org.apache.ignite.internal.processors.cache.persistence.file.FilePageStoreManager;
import org.apache.ignite.internal.processors.query.GridQueryProcessor;
import org.apache.ignite.internal.processors.query.QueryUtils;
import org.apache.ignite.internal.util.typedef.G;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.ListeningTestLogger;
import org.apache.ignite.testframework.LogListener;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.apache.ignite.internal.processors.query.h2.opt.H2TableScanIndex.SCAN_INDEX_NAME_SUFFIX;

/**
 * A set of basic tests for caches with indexes.
 */
public class BasicIndexTest extends AbstractIndexingCommonTest {
    /** Default client name. */
    private static final String CLIENT_NAME = "client";

    /** {@code True} If index need to be created throught static config. */
    private static boolean createIdx = true;

    /** {@code True} If cache nedd to be created throught static config. */
    private static boolean createStaticCache = true;

    /** Default table name. */
    private static final String TEST_TBL_NAME = "PUBLIC.TEST_TABLE";

    /** */
    private Collection<QueryIndex> indexes = Collections.emptyList();

    /** */
    private Integer inlineSize;

    /** */
    private boolean isPersistenceEnabled;

    /** */
    private int gridCount = 1;

    /** Server listening logger. */
    private ListeningTestLogger srvLog;

    /** Client listening logger. */
    private ListeningTestLogger clientLog;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        assertNotNull(inlineSize);

        for (QueryIndex index : indexes)
            index.setInlineSize(inlineSize);

        IgniteConfiguration igniteCfg = super.getConfiguration(igniteInstanceName);

        igniteCfg.setConsistentId(igniteInstanceName);

        if (igniteInstanceName.startsWith(CLIENT_NAME)) {
            igniteCfg.setClientMode(true);

            if (clientLog != null)
                igniteCfg.setGridLogger(clientLog);
        }
        else {
            if (srvLog != null)
                igniteCfg.setGridLogger(srvLog);
        }

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("keyStr", String.class.getName());
        fields.put("keyLong", Long.class.getName());
        fields.put("keyPojo", Pojo.class.getName());
        fields.put("valStr", String.class.getName());
        fields.put("valLong", Long.class.getName());
        fields.put("valPojo", Pojo.class.getName());

        if (!createIdx)
            indexes = Collections.emptyList();

        CacheConfiguration<Key, Val> ccfg = new CacheConfiguration<Key, Val>(DEFAULT_CACHE_NAME)
            .setAffinity(new RendezvousAffinityFunction(false, 32))
            .setQueryEntities(Collections.singleton(
                new QueryEntity()
                    .setKeyType(Key.class.getName())
                    .setValueType(Val.class.getName())
                    .setFields(fields)
                    .setKeyFields(new HashSet<>(Arrays.asList("keyStr", "keyLong", "keyPojo")))
                    .setIndexes(indexes)
                    .setAliases(Collections.singletonMap(QueryUtils.KEY_FIELD_NAME, "pk_id"))
            ))
            .setSqlIndexMaxInlineSize(inlineSize);

        if (createStaticCache)
            igniteCfg.setCacheConfiguration(ccfg);

        if (isPersistenceEnabled) {
            igniteCfg.setDataStorageConfiguration(new DataStorageConfiguration()
                .setDefaultDataRegionConfiguration(
                    new DataRegionConfiguration().setPersistenceEnabled(true).setMaxSize(10 * 1024 * 1024)
                )
            );
        }

        return igniteCfg;
    }

    /**
     * {@inheritDoc}
     */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        stopAllGrids();

        cleanPersistenceDir();
    }

    /**
     * {@inheritDoc}
     */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();

        cleanPersistenceDir();

        srvLog = clientLog = null;

        super.afterTest();
    }

    /**
     * @return Grid count used in test.
     */
    protected int gridCount() {
        return gridCount;
    }

    /** */
    @Test
    public void testNoIndexesNoPersistence() throws Exception {
        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            checkAll();

            stopAllGrids();
        }
    }

    /** */
    @Test
    public void testAllIndexesNoPersistence() throws Exception {
        indexes = Arrays.asList(
            new QueryIndex("keyStr"),
            new QueryIndex("keyLong"),
            new QueryIndex("keyPojo"),
            new QueryIndex("valStr"),
            new QueryIndex("valLong"),
            new QueryIndex("valPojo")
        );

        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            checkAll();

            stopAllGrids();
        }
    }

    /** */
    @Test
    public void testDynamicIndexesNoPersistence() throws Exception {
        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            createDynamicIndexes(
                "keyStr",
                "keyLong",
                "keyPojo",
                "valStr",
                "valLong",
                "valPojo"
            );

            checkAll();

            stopAllGrids();
        }
    }

    /**
     * Tests mixed dynamic and static caches with indexes creation.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDynamicIdxOnStaticCacheWithIdxWithoutPersistence() throws Exception {
        runDynamicIdxOnStaticCacheWithIdx(false);
    }

    /**
     * Tests mixed dynamic and static caches with indexes creation.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDynamicIdxOnStaticCacheWithIdxWithPersistence() throws Exception {
        runDynamicIdxOnStaticCacheWithIdx(true);
    }

    /** */
    private void runDynamicIdxOnStaticCacheWithIdx(boolean persistEnabled) throws Exception {
        isPersistenceEnabled = persistEnabled;

        inlineSize = 10;

        createIdx = false;

        indexes = Collections.singletonList(new QueryIndex("valStr"));

        IgniteEx ig0 = startGrid(0);

        createIdx = true;

        startGrid(1);

        if (persistEnabled)
            ig0.cluster().active(true);

        IgniteCache<Key, Val> cache = grid(0).cache(DEFAULT_CACHE_NAME);

        populateCache();

        String plan = cache.query(new SqlFieldsQuery("explain select * from Val where valStr between 0 and ?")
            .setArgs(100)).getAll().get(0).get(0).toString();

        assertTrue(plan, plan.contains(SCAN_INDEX_NAME_SUFFIX));

        stopAllGrids();

        if (persistEnabled)
            cleanPersistenceDir();

        createStaticCache = false;

        ig0 = startGrid(0);

        if (persistEnabled)
            ig0.cluster().active(true);

        ig0.getOrCreateCache(DEFAULT_CACHE_NAME);

        populateCache();

        createStaticCache = true;

        try {
            startGrid(1);

            fail("Exception wasn't thrown");
        }
        catch (IgniteCheckedException e) {
            // no op.
        }
    }

    /**
     * Tests dynamic indexes creation with equal fields.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testEqualFieldsDynamicIndexesWithoutPersistence() throws Exception {
        runEqualFieldsDynamicIndexes(false);
    }

    /**
     * Tests dynamic indexes creation with equal fields.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testEqualFieldsDynamicIndexesWithPersistence() throws Exception {
        runEqualFieldsDynamicIndexes(true);
    }

    /** */
    private void runEqualFieldsDynamicIndexes(boolean persistEnabled) throws Exception {
        isPersistenceEnabled = persistEnabled;

        indexes = Collections.singletonList(new QueryIndex("valStr"));

        inlineSize = 10;

        srvLog = new ListeningTestLogger(false, log);

        clientLog = new ListeningTestLogger(false, log);

        String msg1 = "Index with the given set or subset of columns already exists";

        LogListener lsnr = LogListener.matches(msg1).andMatches(Pattern.compile(".*newIndexName=idx[0-9]")).build();

        LogListener staticCachesLsnr = LogListener.matches(msg1).build();

        srvLog.registerListener(staticCachesLsnr);

        IgniteEx ig0 = startGrid(0);

        if (persistEnabled)
            ig0.cluster().active(true);

        IgniteCache<Key, Val> cache = grid(0).cache(DEFAULT_CACHE_NAME);

        populateCache();

        cache.query(new SqlFieldsQuery("create index \"idx0\" on Val(valStr)"));

        assertTrue(staticCachesLsnr.check());

        srvLog.unregisterListener(staticCachesLsnr);

        srvLog.registerListener(lsnr);

        cache.query(new SqlFieldsQuery("create index \"idx1\" on Val(valStr, valLong)"));

        cache.query(new SqlFieldsQuery("create index \"idx2\" on Val(valStr desc, valLong)"));

        assertFalse(lsnr.check());

        cache.query(new SqlFieldsQuery("create index \"idx3\" on Val(valStr, valLong)"));

        cache.query(new SqlFieldsQuery("create index \"idx4\" on Val(valLong)"));

        assertTrue(lsnr.check());

        srvLog.unregisterListener(lsnr);

        IgniteEx client = startGrid(CLIENT_NAME);

        cache = client.cache(DEFAULT_CACHE_NAME);

        LogListener lsnrIdx5 = LogListener.matches(msg1).andMatches("idx5").build();

        srvLog.registerListener(lsnrIdx5);

        cache.query(new SqlFieldsQuery("create index \"idx5\" on Val(valStr desc, valLong)"));

        assertTrue(lsnrIdx5.check());

        LogListener lsnrIdx7 = LogListener.matches(msg1).andMatches("idx7").build();

        srvLog.registerListener(lsnrIdx7);

        cache.query(new SqlFieldsQuery("create index \"idx6\" on Val(valLong)"));

        cache.query(new SqlFieldsQuery("create index \"idx7\" on Val(keyStr, keyLong, keyPojo, valLong)"));

        assertFalse(lsnrIdx7.check());
    }

    /** */
    private boolean checkIdxUsed(GridQueryProcessor qryProc, @Nullable String idxName, String tblName, String... reqFlds) {
        String sql = "explain select * from " + tblName + " where ";

        for (int i = 0; i < reqFlds.length; ++i)
            sql += reqFlds[i] + " > 0 " + ((i < reqFlds.length - 1) ? " and " : "");

        String plan = qryProc.querySqlFields(new SqlFieldsQuery(sql), true)
            .getAll().get(0).get(0).toString().toUpperCase();

        return idxName != null ? (!plan.contains(SCAN_INDEX_NAME_SUFFIX) && plan.contains(idxName.toUpperCase())) : !plan.contains(SCAN_INDEX_NAME_SUFFIX);
    }

    /** */
    private boolean checkIdxAlreadyExistLog(GridQueryProcessor qryProc, String idxName, String tblName, String... reqFlds) {
        String msg0 = "Index with the given set or subset of columns already exists";

        String sql = "create index \"" + idxName + "\" on " + tblName + "(";

        for (int i = 0; i < reqFlds.length; ++i)
            sql += reqFlds[i] + ((i < reqFlds.length - 1) ? ", " : ")");

        LogListener lsnrIdx = LogListener.matches(msg0).andMatches(idxName).build();

        srvLog.registerListener(lsnrIdx);

        qryProc.querySqlFields(new SqlFieldsQuery(sql), true).getAll();

        return lsnrIdx.check();
    }

    /** */
    private void populateTable(GridQueryProcessor qryProc, String tblName, int consPkFldsNum, String... reqFlds) {
        assert consPkFldsNum <= reqFlds.length;

        String sql = "CREATE TABLE " + tblName + " (";

        String sqlIns = "INSERT INTO " + tblName + " (";

        for (int i = 0; i < reqFlds.length; ++i) {
            sql += reqFlds[i] + " VARCHAR, ";

            sqlIns += reqFlds[i] + ((i < reqFlds.length - 1) ? ", " : ") values (");
        }

        if (consPkFldsNum > 0) {
            sql += " CONSTRAINT PK_PERSON PRIMARY KEY (";

            for (int i = 0; i < consPkFldsNum; ++i)
                sql += reqFlds[i] + ((i < consPkFldsNum - 1) ? ", " : "))");
        }
        else
            sql += ")";

        qryProc.querySqlFields(new SqlFieldsQuery(sql), true);

        for (int i = 0; i < 10; ++i) {
            String s0 = sqlIns;

            for (int f = 0; f < reqFlds.length; ++f)
                s0 += i + ((f < reqFlds.length - 1) ? ", " : ")");

            qryProc.querySqlFields(new SqlFieldsQuery(s0), true).getAll();
        }
    }

    /**
     *  Checks index usage for full coverage.
     */
    @Test
    public void testAllTableFieldsCoveredByIdx() throws Exception {
        inlineSize = 10;

        srvLog = new ListeningTestLogger(false, log);

        IgniteEx ig0 = startGrid(0);

        GridQueryProcessor qryProc = ig0.context().query();

        populateTable(qryProc, TEST_TBL_NAME, 2, "FIRST_NAME", "LAST_NAME",
            "ADDRESS", "LANG");

        assertFalse(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "LANG"));

        assertFalse(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "LAST_NAME"));

        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "FIRST_NAME"));

        assertTrue(checkIdxUsed(qryProc, "_key_PK", TEST_TBL_NAME, "FIRST_NAME",
            "LAST_NAME", "LANG", "ADDRESS"));

        assertTrue(checkIdxAlreadyExistLog(
            qryProc, "idx1", TEST_TBL_NAME, "FIRST_NAME", "LAST_NAME"));

        String sqlIdx2 = String.format("create index \"idx2\" on %s(LANG, ADDRESS)", TEST_TBL_NAME);

        qryProc.querySqlFields(new SqlFieldsQuery(sqlIdx2), true).getAll();

        assertTrue(checkIdxUsed(qryProc, "idx2", TEST_TBL_NAME, "FIRST_NAME",
            "LAST_NAME", "LANG", "ADDRESS"));

        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "FIRST_NAME",
            "LAST_NAME", "ADDRESS", "LANG"));
        // first idx fields not belongs to request fields.
        assertTrue(checkIdxUsed(qryProc, "idx2", TEST_TBL_NAME, "ADDRESS",
            "LAST_NAME"));
    }

    /**
     *  Checks index usage for partial coverage.
     *  Last field not participate in any index.
     */
    @Test
    public void testPartialTableFieldsCoveredByIdx() throws Exception {
        inlineSize = 10;

        String msg0 = "Index with the given set or subset of columns already exists";

        srvLog = new ListeningTestLogger(false, log);

        IgniteEx ig0 = startGrid(0);

        GridQueryProcessor qryProc = ig0.context().query();

        String cacheName = QueryUtils.createTableCacheName("PUBLIC", "TEST_TABLE");

        populateTable(qryProc, TEST_TBL_NAME, 2, "FIRST_NAME", "LAST_NAME",
            "ADDRESS", "LANG", "GENDER");

        IgniteCache<Object, Object> jcache = ig0.cache(cacheName);

        assertFalse(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "LANG"));

        assertFalse(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "LAST_NAME"));

        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "FIRST_NAME"));

        assertTrue(checkIdxUsed(qryProc, "_key_PK", TEST_TBL_NAME, "FIRST_NAME",
            "LAST_NAME", "LANG", "ADDRESS"));

        assertTrue(checkIdxAlreadyExistLog(
            qryProc, "idx1", TEST_TBL_NAME, "FIRST_NAME", "LAST_NAME"));

        String sqlIdx2 = String.format("create index \"idx2\" on %s(LANG, ADDRESS)", TEST_TBL_NAME);

        qryProc.querySqlFields(new SqlFieldsQuery(sqlIdx2), true).getAll();
        // _key_PK used.
        assertFalse(checkIdxUsed(qryProc, "idx2", TEST_TBL_NAME, "FIRST_NAME",
            "LAST_NAME", "LANG", "ADDRESS"));
        // _key_PK used.
        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "FIRST_NAME",
            "LAST_NAME", "LANG", "ADDRESS"));

        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "FIRST_NAME",
            "LAST_NAME", "ADDRESS", "LANG"));
        // first idx fields not belongs to request fields.
        assertFalse(checkIdxUsed(qryProc, "idx2", TEST_TBL_NAME, "ADDRESS",
            "LAST_NAME"));

        assertFalse(checkIdxAlreadyExistLog(
            qryProc, "idx3", TEST_TBL_NAME, "ADDRESS", "LANG"));

        assertTrue(checkIdxAlreadyExistLog(
            qryProc, "idx4", TEST_TBL_NAME, "FIRST_NAME", "LAST_NAME", "ADDRESS", "LANG"));

        LogListener lsnrIdx4 = LogListener.matches(msg0).andMatches("_key_PK").build();

        srvLog.registerListener(lsnrIdx4);

        String sqlIdx5 = String.format("create index \"idx5\" on %s(FIRST_NAME, LAST_NAME, LANG, ADDRESS)", TEST_TBL_NAME);

        jcache.query(new SqlFieldsQuery(sqlIdx5)).getAll();

        assertTrue(lsnrIdx4.check());
    }

    /**
     * Tests different fields sequence in indexes.
     */
    @Test
    public void testCreateIdxWithDifferentIdxFldsSeq() throws Exception {
        inlineSize = 10;

        srvLog = new ListeningTestLogger(false, log);

        IgniteEx ig0 = startGrid(0);

        IgniteEx client = startGrid(CLIENT_NAME);

        GridQueryProcessor qryProc = ig0.context().query();

        populateTable(qryProc, TEST_TBL_NAME, 1, "c1", "c2", "c3", "c4", "c5");

        assertFalse(checkIdxAlreadyExistLog(
            qryProc, "idx1", TEST_TBL_NAME, "c1", "c2", "c3", "c4", "c5"));

        assertFalse(checkIdxAlreadyExistLog(
            qryProc, "idx2", TEST_TBL_NAME, "c1", "c3", "c4", "c5"));

        assertTrue(checkIdxAlreadyExistLog(
            qryProc, "idx3", TEST_TBL_NAME, "c1", "c2"));

        assertTrue(checkIdxAlreadyExistLog(
            qryProc, "idx4", TEST_TBL_NAME, "c1", "c3"));

        assertFalse(checkIdxAlreadyExistLog(
            qryProc, "idx5", TEST_TBL_NAME, "c1", "c4", "c5"));

        GridQueryProcessor qryProcCl = client.context().query();

        assertTrue(checkIdxAlreadyExistLog(
            qryProcCl, "idx6", TEST_TBL_NAME, "c1", "c2"));
    }

    /**
     * Tests different fields sequence in indexes.
     * Last field not participate in any index.
     */
    @Test
    public void testIndexWithDifferentFldsReqPartialFldsInIdx() throws Exception {
        inlineSize = 10;

        IgniteEx ig0 = startGrid(0);

        GridQueryProcessor qryProc = ig0.context().query();

        populateTable(qryProc, TEST_TBL_NAME, 2, "FIRST_NAME", "LAST_NAME",
            "ADDRESS", "LANG", "GENDER");

        String sqlIdx1 = String.format("create index \"idx1\" on %s(LANG, LAST_NAME, ADDRESS, FIRST_NAME)", TEST_TBL_NAME);

        qryProc.querySqlFields(new SqlFieldsQuery(sqlIdx1), true).getAll();

        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "FIRST_NAME", "LAST_NAME", "LANG"));

        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "FIRST_NAME", "LAST_NAME", "ADDRESS"));

        assertFalse(checkIdxUsed(qryProc, null, TEST_TBL_NAME,  "LAST_NAME", "ADDRESS"));
    }

    /**
     * Tests different fields sequence in indexes.
     * All fields covered by indexes.
     */
    @Test
    public void testIndexWithDifferentFldsReqAllFldsInIdx() throws Exception {
        inlineSize = 10;

        IgniteEx ig0 = startGrid(0);

        GridQueryProcessor qryProc = ig0.context().query();

        populateTable(qryProc, TEST_TBL_NAME, 2, "FIRST_NAME", "LAST_NAME",
            "ADDRESS", "LANG");

        String sqlIdx1 = String.format("create index \"idx1\" on %s(LANG, LAST_NAME, ADDRESS, FIRST_NAME)", TEST_TBL_NAME);

        qryProc.querySqlFields(new SqlFieldsQuery(sqlIdx1), true).getAll();

        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "FIRST_NAME", "LAST_NAME", "LANG"));

        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME, "FIRST_NAME", "LAST_NAME", "ADDRESS"));

        assertTrue(checkIdxUsed(qryProc, null, TEST_TBL_NAME,  "LAST_NAME", "ADDRESS"));
    }

    /** */
    @Test
    public void testNoIndexesWithPersistence() throws Exception {
        isPersistenceEnabled = true;

        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            checkAll();

            stopAllGrids();

            startGridsMultiThreaded(gridCount());

            checkAll();

            stopAllGrids();

            cleanPersistenceDir();
        }
    }

    /** */
    @Test
    public void testAllIndexesWithPersistence() throws Exception {
        indexes = Arrays.asList(
            new QueryIndex("keyStr"),
            new QueryIndex("keyLong"),
            new QueryIndex("keyPojo"),
            new QueryIndex("valStr"),
            new QueryIndex("valLong"),
            new QueryIndex("valPojo")
        );

        isPersistenceEnabled = true;

        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            checkAll();

            stopAllGrids();

            startGridsMultiThreaded(gridCount());

            checkAll();

            stopAllGrids();

            cleanPersistenceDir();
        }
    }

    /** */
    @Test
    public void testDynamicIndexesWithPersistence() throws Exception {
        isPersistenceEnabled = true;

        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            createDynamicIndexes(
                "keyStr",
                "keyLong",
                "keyPojo",
                "valStr",
                "valLong",
                "valPojo"
            );

            checkAll();

            stopAllGrids();

            startGridsMultiThreaded(gridCount());

            checkAll();

            stopAllGrids();

            cleanPersistenceDir();
        }
    }

    /** */
    @Test
    public void testDynamicIndexesDropWithPersistence() throws Exception {
        isPersistenceEnabled = true;

        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            String[] cols = {
                "keyStr",
                "keyLong",
                "keyPojo",
                "valStr",
                "valLong",
                "valPojo"
            };

            createDynamicIndexes(cols);

            checkAll();

            dropDynamicIndexes(cols);

            checkAll();

            stopAllGrids();

            startGridsMultiThreaded(gridCount());

            checkAll();

            stopAllGrids();

            cleanPersistenceDir();
        }
    }

    /** */
    @Test
    public void testNoIndexesWithPersistenceIndexRebuild() throws Exception {
        isPersistenceEnabled = true;

        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            checkAll();

            List<Path> idxPaths = getIndexBinPaths();

            // Shutdown gracefully to ensure there is a checkpoint with index.bin.
            // Otherwise index.bin rebuilding may not work.
            grid(0).cluster().active(false);

            stopAllGrids();

            idxPaths.forEach(idxPath -> assertTrue(U.delete(idxPath)));

            startGridsMultiThreaded(gridCount());

            grid(0).cache(DEFAULT_CACHE_NAME).indexReadyFuture().get();

            checkAll();

            stopAllGrids();

            cleanPersistenceDir();
        }
    }

    /** */
    @Test
    public void testAllIndexesWithPersistenceIndexRebuild() throws Exception {
        indexes = Arrays.asList(
            new QueryIndex("keyStr"),
            new QueryIndex("keyLong"),
            new QueryIndex("keyPojo"),
            new QueryIndex("valStr"),
            new QueryIndex("valLong"),
            new QueryIndex("valPojo")
        );

        isPersistenceEnabled = true;

        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            checkAll();

            List<Path> idxPaths = getIndexBinPaths();

            // Shutdown gracefully to ensure there is a checkpoint with index.bin.
            // Otherwise index.bin rebuilding may not work.
            grid(0).cluster().active(false);

            stopAllGrids();

            idxPaths.forEach(idxPath -> assertTrue(U.delete(idxPath)));

            startGridsMultiThreaded(gridCount());

            grid(0).cache(DEFAULT_CACHE_NAME).indexReadyFuture().get();

            checkAll();

            stopAllGrids();

            cleanPersistenceDir();
        }
    }

    /** */
    @Test
    public void testDynamicIndexesWithPersistenceIndexRebuild() throws Exception {
        isPersistenceEnabled = true;

        int[] inlineSizes = {0, 10, 20, 50, 100};

        for (int i : inlineSizes) {
            log().info("Checking inlineSize=" + i);

            inlineSize = i;

            startGridsMultiThreaded(gridCount());

            populateCache();

            createDynamicIndexes(
                "keyStr",
                "keyLong",
                "keyPojo",
                "valStr",
                "valLong",
                "valPojo"
            );

            checkAll();

            List<Path> idxPaths = getIndexBinPaths();

            // Shutdown gracefully to ensure there is a checkpoint with index.bin.
            // Otherwise index.bin rebuilding may not work.
            grid(0).cluster().active(false);

            stopAllGrids();

            idxPaths.forEach(idxPath -> assertTrue(U.delete(idxPath)));

            startGridsMultiThreaded(gridCount());

            grid(0).cache(DEFAULT_CACHE_NAME).indexReadyFuture().get();

            checkAll();

            stopAllGrids();

            cleanPersistenceDir();
        }
    }

    /** */
    private void checkAll() {
        IgniteCache<Key, Val> cache = grid(0).cache(DEFAULT_CACHE_NAME);

        checkRemovePut(cache);

        checkSelectAll(cache);

        checkSelectStringEqual(cache);

        checkSelectLongEqual(cache);

        checkSelectStringRange(cache);

        checkSelectLongRange(cache);
    }

    /** */
    private void populateCache() {
        IgniteCache<Key, Val> cache = grid(0).cache(DEFAULT_CACHE_NAME);

        // Be paranoid and populate first even indexes in ascending order, then odd indexes in descending
        // to check that inserting in the middle works.

        for (int i = 0; i < 100; i += 2)
            cache.put(key(i), val(i));

        for (int i = 99; i > 0; i -= 2)
            cache.put(key(i), val(i));

        for (int i = 99; i > 0; i -= 2)
            assertEquals(val(i), cache.get(key(i)));
    }

    /** */
    private void checkRemovePut(IgniteCache<Key, Val> cache) {
        final int INT = 24;

        assertEquals(val(INT), cache.get(key(INT)));

        cache.remove(key(INT));

        assertNull(cache.get(key(INT)));

        cache.put(key(INT), val(INT));

        assertEquals(val(INT), cache.get(key(INT)));
    }

    /** */
    private void checkSelectAll(IgniteCache<Key, Val> cache) {
        List<List<?>> data = cache.query(new SqlFieldsQuery("select _key, _val from Val")).getAll();

        assertEquals(100, data.size());

        for (List<?> row : data) {
            Key key = (Key) row.get(0);

            Val val = (Val) row.get(1);

            long i = key.keyLong;

            assertEquals(key(i), key);

            assertEquals(val(i), val);
        }
    }

    /** */
    private void checkSelectStringEqual(IgniteCache<Key, Val> cache) {
        final String STR = "foo011";

        final long LONG = 11;

        List<List<?>> data = cache.query(new SqlFieldsQuery("select _key, _val from Val where keyStr = ?")
            .setArgs(STR))
            .getAll();

        assertEquals(1, data.size());

        List<?> row = data.get(0);

        assertEquals(key(LONG), row.get(0));

        assertEquals(val(LONG), row.get(1));
    }

    /** */
    private void checkSelectLongEqual(IgniteCache<Key, Val> cache) {
        final long LONG = 42;

        List<List<?>> data = cache.query(new SqlFieldsQuery("select _key, _val from Val where valLong = ?")
            .setArgs(LONG))
            .getAll();

        assertEquals(1, data.size());

        List<?> row = data.get(0);

        assertEquals(key(LONG), row.get(0));

        assertEquals(val(LONG), row.get(1));
    }

    /** */
    private void checkSelectStringRange(IgniteCache<Key, Val> cache) {
        final String PREFIX = "foo06";

        List<List<?>> data = cache.query(new SqlFieldsQuery("select _key, _val from Val where keyStr like ?")
            .setArgs(PREFIX + "%"))
            .getAll();

        assertEquals(10, data.size());

        for (List<?> row : data) {
            Key key = (Key) row.get(0);

            Val val = (Val) row.get(1);

            long i = key.keyLong;

            assertEquals(key(i), key);

            assertEquals(val(i), val);

            assertTrue(key.keyStr.startsWith(PREFIX));
        }
    }

    /** */
    private void checkSelectLongRange(IgniteCache<Key, Val> cache) {
        final long RANGE_START = 70;

        final long RANGE_END = 80;

        List<List<?>> data = cache.query(
            new SqlFieldsQuery("select _key, _val from Val where valLong >= ? and valLong < ?")
                .setArgs(RANGE_START, RANGE_END))
            .getAll();

        assertEquals(10, data.size());

        for (List<?> row : data) {
            Key key = (Key) row.get(0);

            Val val = (Val) row.get(1);

            long i = key.keyLong;

            assertEquals(key(i), key);

            assertEquals(val(i), val);

            assertTrue(i >= RANGE_START && i < RANGE_END);
        }
    }

    /**
     * Must be called when the grid is up.
     */
    private List<Path> getIndexBinPaths() {
        return G.allGrids().stream()
            .map(grid -> (IgniteEx) grid)
            .map(grid -> {
                IgniteInternalCache<Object, Object> cachex = grid.cachex(DEFAULT_CACHE_NAME);

                assertNotNull(cachex);

                FilePageStoreManager pageStoreMgr = (FilePageStoreManager) cachex.context().shared().pageStore();

                assertNotNull(pageStoreMgr);

                File cacheWorkDir = pageStoreMgr.cacheWorkDir(cachex.configuration());

                return cacheWorkDir.toPath().resolve("index.bin");
            })
            .collect(Collectors.toList());
    }

    /** */
    private void createDynamicIndexes(String... cols) {
        IgniteCache<Key, Val> cache = grid(0).cache(DEFAULT_CACHE_NAME);

        for (String col : cols) {
            String indexName = col + "_idx";
            String schemaName = DEFAULT_CACHE_NAME;

            cache.query(new SqlFieldsQuery(
                String.format("create index %s on \"%s\".Val(%s) INLINE_SIZE %s;", indexName, schemaName, col, inlineSize)
            )).getAll();
        }

        cache.indexReadyFuture().get();
    }

    /** */
    private void dropDynamicIndexes(String... cols) {
        IgniteCache<Key, Val> cache = grid(0).cache(DEFAULT_CACHE_NAME);

        for (String col : cols) {
            String indexName = col + "_idx";

            cache.query(new SqlFieldsQuery(
                String.format("drop index %s;", indexName)
            )).getAll();
        }

        cache.indexReadyFuture().get();
    }

    /** */
    private static Key key(long i) {
        return new Key(String.format("foo%03d", i), i, new Pojo(i));
    }

    /** */
    private static Val val(long i) {
        return new Val(String.format("bar%03d", i), i, new Pojo(i));
    }

    /** */
    private static class Key {
        /** */
        private String keyStr;

        /** */
        private long keyLong;

        /** */
        private Pojo keyPojo;

        /** */
        private Key(String str, long aLong, Pojo pojo) {
            keyStr = str;
            keyLong = aLong;
            keyPojo = pojo;
        }

        /**
         * {@inheritDoc}
         */
        @Override public boolean equals(Object o) {
            if (this == o)
                return true;

            if (o == null || getClass() != o.getClass())
                return false;

            Key key = (Key) o;

            return keyLong == key.keyLong &&
                Objects.equals(keyStr, key.keyStr) &&
                Objects.equals(keyPojo, key.keyPojo);
        }

        /**
         * {@inheritDoc}
         */
        @Override public int hashCode() {
            return Objects.hash(keyStr, keyLong, keyPojo);
        }

        /**
         * {@inheritDoc}
         */
        @Override public String toString() {
            return S.toString(Key.class, this);
        }
    }

    /** */
    private static class Val {
        /** */
        private String valStr;

        /** */
        private long valLong;

        /** */
        private Pojo valPojo;

        /** */
        private Val(String str, long aLong, Pojo pojo) {
            valStr = str;
            valLong = aLong;
            valPojo = pojo;
        }

        /**
         * {@inheritDoc}
         */
        @Override public boolean equals(Object o) {
            if (this == o)
                return true;

            if (o == null || getClass() != o.getClass())
                return false;

            Val val = (Val) o;

            return valLong == val.valLong &&
                Objects.equals(valStr, val.valStr) &&
                Objects.equals(valPojo, val.valPojo);
        }

        /**
         * {@inheritDoc}
         */
        @Override public int hashCode() {
            return Objects.hash(valStr, valLong, valPojo);
        }

        /**
         * {@inheritDoc}
         */
        @Override public String toString() {
            return S.toString(Val.class, this);
        }
    }

    /** */
    private static class Pojo {
        /** */
        private long pojoLong;

        /** */
        private Pojo(long pojoLong) {
            this.pojoLong = pojoLong;
        }

        /**
         * {@inheritDoc}
         */
        @Override public boolean equals(Object o) {
            if (this == o)
                return true;

            if (o == null || getClass() != o.getClass())
                return false;

            Pojo pojo = (Pojo) o;

            return pojoLong == pojo.pojoLong;
        }

        /**
         * {@inheritDoc}
         */
        @Override public int hashCode() {
            return Objects.hash(pojoLong);
        }

        /**
         * {@inheritDoc}
         */
        @Override public String toString() {
            return S.toString(Pojo.class, this);
        }
    }
}
