package com.affiliate.platform.reporting;

import com.affiliate.platform.entity.ReportCohortAcquisitionEntity;
import com.affiliate.platform.entity.ReportCohortActivityEntity;
import com.affiliate.platform.mapper.ReportCohortAcquisitionMapper;
import com.affiliate.platform.mapper.ReportCohortActivityMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 留存率与用户生命周期价值 (LTV) 队列衰减分析服务 (Cohort Retention & LTV Analysis Service)
 * <p>
 * 商业级网盟与买量增长平台标配能力（参考 Singular, Adjust, AppsFlyer）：
 * 1. 按用户初始获客日期 (Cohort Acquisition Date) 对新增人群分群；
 * 2. 追踪 D1, D3, D7, D14, D30 滚动留存率衰减曲线；
 * 3. 追踪 D0~D30 累积 LTV (Lifetime Value) 增长曲线与单客获客成本 (CAC)；
 * 4. 自动计算投资回收周期 (Payback Period) 与 D30 回本投资回报率 (ROAS/ROI)；
 * 5. 全量异步接入 PostgreSQL `report_cohort_acquisition` 与 `report_cohort_activity` 存储。
 */
@Service
public class CohortAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CohortAnalysisService.class);
    public static final List<Integer> COHORT_DAYS = List.of(0, 1, 3, 7, 14, 30);

    /**
     * 单个队列分析记录实体
     */
    public record CohortRow(
            LocalDate cohortDate,
            long cohortSize,
            BigDecimal acquisitionCost,
            BigDecimal cac,
            Map<Integer, Double> retentionRates,   // Key: Day (1, 3, 7, 14, 30), Value: 百分比 (0~100)
            Map<Integer, BigDecimal> cumulativeLtv, // Key: Day (0, 1, 3, 7, 14, 30), Value: 单客累计 LTV
            Integer paybackDay,                     // 回本天数（若未回本则为 null）
            Double roiD30                           // D30 回本率 %
    ) {}

    /**
     * 队列矩阵报表实体
     */
    public record CohortMatrix(
            LocalDate fromDate,
            LocalDate toDate,
            long totalAcquiredUsers,
            BigDecimal totalSpend,
            List<CohortRow> rows
    ) {}

    private record UserAcquisition(
            String userId,
            LocalDate cohortDate,
            BigDecimal cost
    ) {}

    private record UserActivity(
            LocalDate date,
            BigDecimal revenue
    ) {}

    private final ConcurrentMap<String, UserAcquisition> acquisitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<UserActivity>> activities = new ConcurrentHashMap<>();

    private final ReportCohortAcquisitionMapper acquisitionMapper;
    private final ReportCohortActivityMapper activityMapper;

    @Autowired
    public CohortAnalysisService(
            @Autowired(required = false) ReportCohortAcquisitionMapper acquisitionMapper,
            @Autowired(required = false) ReportCohortActivityMapper activityMapper
    ) {
        this.acquisitionMapper = acquisitionMapper;
        this.activityMapper = activityMapper;
    }

    public CohortAnalysisService() {
        this(null, null);
    }

    /**
     * 登记用户初始转化获客
     */
    public void recordAcquisition(String userId, LocalDate cohortDate, BigDecimal cost) {
        if (userId == null || cohortDate == null) return;
        BigDecimal effectiveCost = cost == null ? BigDecimal.ZERO : cost;
        acquisitions.put(userId, new UserAcquisition(userId, cohortDate, effectiveCost));

        if (acquisitionMapper != null) {
            Thread.ofVirtual().name("cohort-acq-persister").start(() -> {
                try {
                    ReportCohortAcquisitionEntity entity = new ReportCohortAcquisitionEntity(
                            UUID.randomUUID().toString(), "default", userId, cohortDate, effectiveCost, Instant.now()
                    );
                    acquisitionMapper.insert(entity);
                } catch (Exception e) {
                    log.error("Failed to persist cohort acquisition for user {}: {}", userId, e.getMessage());
                }
            });
        }
    }

    /**
     * 登记用户后续回访活跃与产生营收
     */
    public void recordActivity(String userId, LocalDate activityDate, BigDecimal revenue) {
        if (userId == null || activityDate == null) return;
        BigDecimal effectiveRevenue = revenue == null ? BigDecimal.ZERO : revenue;
        activities.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new UserActivity(activityDate, effectiveRevenue));

        if (activityMapper != null) {
            Thread.ofVirtual().name("cohort-act-persister").start(() -> {
                try {
                    ReportCohortActivityEntity entity = new ReportCohortActivityEntity(
                            UUID.randomUUID().toString(), "default", userId, activityDate, effectiveRevenue, Instant.now()
                    );
                    activityMapper.insert(entity);
                } catch (Exception e) {
                    log.error("Failed to persist cohort activity for user {}: {}", userId, e.getMessage());
                }
            });
        }
    }

    /**
     * 计算并生成指定日期范围的 Cohort 矩阵报表
     */
    public CohortMatrix generateMatrix(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate end = to == null ? LocalDate.now() : to;

        // 若本地内存为空且已接入数据库，则从 PostgreSQL 自动回源加载指定区间数据
        if (acquisitions.isEmpty() && acquisitionMapper != null) {
            try {
                LambdaQueryWrapper<ReportCohortAcquisitionEntity> acqQuery = new LambdaQueryWrapper<>();
                acqQuery.ge(ReportCohortAcquisitionEntity::getCohortDate, start)
                        .le(ReportCohortAcquisitionEntity::getCohortDate, end);
                List<ReportCohortAcquisitionEntity> dbAcqs = acquisitionMapper.selectList(acqQuery);
                for (ReportCohortAcquisitionEntity e : dbAcqs) {
                    acquisitions.putIfAbsent(e.getUserId(), new UserAcquisition(e.getUserId(), e.getCohortDate(), e.getCost()));
                }

                if (activityMapper != null && !dbAcqs.isEmpty()) {
                    List<String> userIds = dbAcqs.stream().map(ReportCohortAcquisitionEntity::getUserId).toList();
                    LambdaQueryWrapper<ReportCohortActivityEntity> actQuery = new LambdaQueryWrapper<>();
                    actQuery.in(ReportCohortActivityEntity::getUserId, userIds);
                    List<ReportCohortActivityEntity> dbActs = activityMapper.selectList(actQuery);
                    for (ReportCohortActivityEntity act : dbActs) {
                        activities.computeIfAbsent(act.getUserId(), k -> Collections.synchronizedList(new ArrayList<>()))
                                .add(new UserActivity(act.getActivityDate(), act.getRevenue()));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load cohort data from database: {}", e.getMessage());
            }
        }

        // 1. 过滤在日期区间内的获客用户
        Map<LocalDate, List<UserAcquisition>> byCohortDate = new TreeMap<>();
        long totalUsers = 0;
        BigDecimal totalSpend = BigDecimal.ZERO;

        for (UserAcquisition acq : acquisitions.values()) {
            if (!acq.cohortDate().isBefore(start) && !acq.cohortDate().isAfter(end)) {
                byCohortDate.computeIfAbsent(acq.cohortDate(), k -> new ArrayList<>()).add(acq);
                totalUsers++;
                totalSpend = totalSpend.add(acq.cost());
            }
        }

        List<CohortRow> rows = new ArrayList<>();

        for (Map.Entry<LocalDate, List<UserAcquisition>> entry : byCohortDate.entrySet()) {
            LocalDate cDate = entry.getKey();
            List<UserAcquisition> userList = entry.getValue();
            long cohortSize = userList.size();
            BigDecimal cohortCost = userList.stream().map(UserAcquisition::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cac = cohortSize <= 0 ? BigDecimal.ZERO : cohortCost.divide(BigDecimal.valueOf(cohortSize), 4, RoundingMode.HALF_UP);

            // 统计该 Cohort 各活跃天的独立活跃用户数与累计营收
            Map<Integer, Set<String>> activeUsersByDay = new HashMap<>();
            Map<Integer, BigDecimal> revenueByDay = new HashMap<>();
            for (int day : COHORT_DAYS) {
                activeUsersByDay.put(day, new HashSet<>());
                revenueByDay.put(day, BigDecimal.ZERO);
            }

            for (UserAcquisition u : userList) {
                // D0 自动包含所有初始获客
                activeUsersByDay.get(0).add(u.userId());

                List<UserActivity> acts = activities.get(u.userId());
                if (acts != null) {
                    synchronized (acts) {
                        for (UserActivity act : acts) {
                            long daysDiff = ChronoUnit.DAYS.between(cDate, act.date());
                            if (daysDiff >= 0 && daysDiff <= 30) {
                                int d = (int) daysDiff;
                                for (int checkDay : COHORT_DAYS) {
                                    if (d <= checkDay) {
                                        // 累计营收落在对应截断日
                                        revenueByDay.merge(checkDay, act.revenue(), BigDecimal::add);
                                    }
                                    if (d == checkDay) {
                                        activeUsersByDay.get(checkDay).add(u.userId());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 计算留存率百分比与累计单客 LTV
            Map<Integer, Double> retentionMap = new LinkedHashMap<>();
            Map<Integer, BigDecimal> ltvMap = new LinkedHashMap<>();
            Integer paybackDay = null;

            for (int d : COHORT_DAYS) {
                if (d > 0) {
                    double retRate = cohortSize <= 0 ? 0.0 : ((double) activeUsersByDay.get(d).size() / cohortSize) * 100.0;
                    retentionMap.put(d, BigDecimal.valueOf(retRate).setScale(2, RoundingMode.HALF_UP).doubleValue());
                }

                BigDecimal cumRev = revenueByDay.getOrDefault(d, BigDecimal.ZERO);
                BigDecimal ltv = cohortSize <= 0 ? BigDecimal.ZERO : cumRev.divide(BigDecimal.valueOf(cohortSize), 4, RoundingMode.HALF_UP);
                ltvMap.put(d, ltv);

                if (paybackDay == null && cac.signum() > 0 && ltv.compareTo(cac) >= 0) {
                    paybackDay = d;
                }
            }

            // 计算 D30 ROI
            Double roiD30 = null;
            if (cohortCost.signum() > 0) {
                BigDecimal revD30 = revenueByDay.getOrDefault(30, BigDecimal.ZERO);
                BigDecimal profitD30 = revD30.subtract(cohortCost);
                double roi = profitD30.divide(cohortCost, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
                roiD30 = BigDecimal.valueOf(roi).setScale(2, RoundingMode.HALF_UP).doubleValue();
            }

            rows.add(new CohortRow(cDate, cohortSize, cohortCost, cac, retentionMap, ltvMap, paybackDay, roiD30));
        }

        return new CohortMatrix(start, end, totalUsers, totalSpend, rows);
    }
}
