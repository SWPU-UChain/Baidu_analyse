package cn.edu.swpu.cins.event.analyse.platform.service.impl;

import cn.edu.swpu.cins.event.analyse.platform.dao.DailyEventDao;
import cn.edu.swpu.cins.event.analyse.platform.dao.HandledEventDao;
import cn.edu.swpu.cins.event.analyse.platform.dao.SpecialPostDao;
import cn.edu.swpu.cins.event.analyse.platform.dao.SpecialPostEventDao;
import cn.edu.swpu.cins.event.analyse.platform.enums.ChartDataTypeEnum;
import cn.edu.swpu.cins.event.analyse.platform.enums.ChartTypeEnum;
import cn.edu.swpu.cins.event.analyse.platform.exception.BaseException;
import cn.edu.swpu.cins.event.analyse.platform.exception.IlleagalArgumentException;
import cn.edu.swpu.cins.event.analyse.platform.exception.NoEventException;
import cn.edu.swpu.cins.event.analyse.platform.model.persistence.DailyEvent;
import cn.edu.swpu.cins.event.analyse.platform.model.persistence.HandledEvent;
import cn.edu.swpu.cins.event.analyse.platform.model.view.ChartPoint;
import cn.edu.swpu.cins.event.analyse.platform.model.view.Post;
import cn.edu.swpu.cins.event.analyse.platform.model.view.SpecialPostEventChart;
import cn.edu.swpu.cins.event.analyse.platform.service.ReportService;
import cn.edu.swpu.cins.event.analyse.platform.service.SpecialPostEventChartService;
import cn.edu.swpu.cins.event.analyse.platform.utility.chart.generator.ChartGenerator;
import org.jfree.chart.JFreeChart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by LLPP on 2017/6/19.
 */
@Service
public class ReportServiceImpl implements ReportService {
    private final String[] monthsChar = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二"};

    private ChartGenerator chartGenerator;
    private DailyEventDao dailyEventDao;
    private HandledEventDao handledEventDao;
    private SpecialPostDao specialPostDao;
    private SpecialPostEventDao specialPostEventDao;
    private SpecialPostEventChartService specialPostEventChartService;

    @Autowired
    public ReportServiceImpl(DailyEventDao dailyEventDao
            , HandledEventDao handledEventDao
            , ChartGenerator chartGenerator
            , SpecialPostEventDao specialPostEventDao
            , SpecialPostDao specialPostDao
            , SpecialPostEventChartService specialPostEventChartService) {
        this.specialPostEventChartService = specialPostEventChartService;
        this.specialPostDao = specialPostDao;
        this.specialPostEventDao = specialPostEventDao;
        this.handledEventDao = handledEventDao;
        this.dailyEventDao = dailyEventDao;
        this.chartGenerator = chartGenerator;
    }

    @Override
    public Map<String, Object> getReportDataMap(int year, int issue) throws Exception {
        try {
            Map<String, Object> reportDataMap = new HashMap<>();

            int beginMonth = issue;//起始月

            int endMonth = issue + 1;//结束月

            LocalDateTime localTime = LocalDateTime.now();

            //日期校验
            if (issue > 11 || issue < 1 || localTime.isBefore(LocalDateTime.of(year, endMonth, 15, 0, 0, 0, 0)))
                throw new IlleagalArgumentException();

            Date beginTime;//检索开始日期

            Date endTime;//检索结束日期

            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd 00:00:00");

            Calendar calendar = new GregorianCalendar(year, beginMonth - 1, 1, 0, 0, 0);//不可使用无参构造器，因为会读入系统时间的毫秒数造成干扰

            calendar.set(year, beginMonth - 1, 1, 0, 0, 0); //Month value is 0-based. e.g., 0 for January.

            beginTime = calendar.getTime();

            calendar.set(year, endMonth, 1, 0, 0, 0);

            endTime = calendar.getTime();

            List<DailyEvent> dailyEventList = dailyEventDao.selectByGivenTimes(dateFormat.format(beginTime), dateFormat.format(endTime), "百度贴吧", false);

            List<HandledEvent> handledEventList = handledEventDao.selectByGivenTimes(dateFormat.format(beginTime), dateFormat.format(endTime), "百度贴吧");

            List<DailyEvent> beginList = dailyEventList.stream()
                    .filter(dailyEvent -> {
                        calendar.setTime(dailyEvent.getPostTime());
                        return calendar.get(Calendar.MONTH) % issue != 0; //Month value is 0-based. e.g., 0 for January.
                    })
                    .collect(Collectors.toList());//奇数月事件列表

            List<DailyEvent> endList = dailyEventList.stream()
                    .filter(dailyEvent -> {
                        calendar.setTime(dailyEvent.getPostTime());
                        return calendar.get(Calendar.MONTH) % issue == 0;
                    })
                    .collect(Collectors.toList());//偶数月事件列表

            int beginEventCount = beginList.size();//奇数月事件数目

            int endEventCount = endList.size();//偶数月事件数目

            int beginCommentCount = (int) beginList
                    .stream()
                    .mapToInt(DailyEvent::getFollowCount)
                    .summaryStatistics()
                    .getSum();//奇数月事件评论数

            int endCommentCount = (int) endList
                    .stream()
                    .mapToInt(DailyEvent::getFollowCount)
                    .summaryStatistics()
                    .getSum();//偶数月事件评论数

            int beginHandledCount = (int) handledEventList
                    .stream()
                    .filter(dailyEvent -> {
                        calendar.setTime(dailyEvent.getPostTime());
                        return calendar.get(Calendar.MONTH) % issue != 0 && dailyEvent.getFeedbackCondition() == 1; //Month value is 0-based. e.g., 0 for January.
                    })
                    .count();//起始月事件妥善处置数

            int endHandledCount = (int) handledEventList
                    .stream()
                    .filter(dailyEvent -> {
                        calendar.setTime(dailyEvent.getPostTime());
                        return calendar.get(Calendar.MONTH) % issue == 0 && dailyEvent.getFeedbackCondition() == 1; //Month value is 0-based. e.g., 0 for January.
                    })
                    .count();//结束月事件妥善处置数

            int heatCount = (int) dailyEventList
                    .stream()
                    .filter(dailyEvent -> (dailyEvent.getFollowCount() > 20))
                    .count();//热点事件数

            int eventCount = endEventCount + beginEventCount;//统计事件总数

            String beginMonthChar = monthsChar[beginMonth];//奇月中文数字

            String endMonthChar = monthsChar[endMonth];//偶数月中文数字

            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
            String generateDate = dateTimeFormatter.format(LocalDateTime.now());//报表生成日期

            calendar.setTime(endTime);
            calendar.add(Calendar.DATE, -1);
            Date endDateOfEndMonth = calendar.getTime();
            List<ChartPoint> pointList = chartGenerator.getChartPoints(dailyEventList, beginTime.getTime(), endDateOfEndMonth.getTime(), ChartDataTypeEnum.POSTCOUNT);
            JFreeChart doubleMonthChart = chartGenerator.generateChart(pointList, "专题信息量趋势图", ChartTypeEnum.DOUBLE_MONTH);

            calendar.setTime(beginTime);
            calendar.add(Calendar.MONTH, 1);
            calendar.add(Calendar.DATE, -1);
            Date endDateOfBeginMonth = calendar.getTime();
            List<ChartPoint> pointList1 = chartGenerator.getChartPoints(beginList, beginTime.getTime(), endDateOfBeginMonth.getTime(), ChartDataTypeEnum.POSTCOUNT);
            JFreeChart beginMonthChart = chartGenerator.generateChart(pointList1, beginMonthChar + "月份贴吧主题数趋势图", ChartTypeEnum.SINGLE_MONTH);

            List<ChartPoint> pointList2 = chartGenerator.getChartPoints(beginList, beginTime.getTime(), endDateOfBeginMonth.getTime(), ChartDataTypeEnum.FOLOWCOUNT);
            JFreeChart beginCommentChart = chartGenerator.generateChart(pointList2, beginMonthChar + "月份贴吧跟帖数趋势图", ChartTypeEnum.SINGLE_MONTH);

            calendar.setTime(endDateOfBeginMonth);
            calendar.add(Calendar.DATE, 1);
            Date beginDateOfEndMonth = calendar.getTime();
            List<ChartPoint> pointList3 = chartGenerator.getChartPoints(endList, beginDateOfEndMonth.getTime(), endDateOfEndMonth.getTime(), ChartDataTypeEnum.POSTCOUNT);
            JFreeChart endMonthChart = chartGenerator.generateChart(pointList3, endMonthChar + "月份贴吧主题数趋势图", ChartTypeEnum.SINGLE_MONTH);

            List<ChartPoint> pointList4 = chartGenerator.getChartPoints(endList, beginDateOfEndMonth.getTime(), endDateOfEndMonth.getTime(), ChartDataTypeEnum.FOLOWCOUNT);
            JFreeChart endCommentChart = chartGenerator.generateChart(pointList4, endMonthChar + "月份贴吧跟帖数趋势图", ChartTypeEnum.SINGLE_MONTH);

            reportDataMap.put("year", year);
            reportDataMap.put("generateDate", generateDate);
            reportDataMap.put("beginMonth", beginMonth);
            reportDataMap.put("endMonth", endMonth);
            reportDataMap.put("beginMonthChar", beginMonthChar);
            reportDataMap.put("endMonthChar", endMonthChar);
            reportDataMap.put("beginCommentCount", beginCommentCount);
            reportDataMap.put("endCommentCount", endCommentCount);
            reportDataMap.put("endHandledCount", endHandledCount);
            reportDataMap.put("beginHandledCount", beginHandledCount);
            reportDataMap.put("beginEventCount", beginEventCount);
            reportDataMap.put("endEventCount", endEventCount);
            reportDataMap.put("heatCount", heatCount);
            reportDataMap.put("eventCount", eventCount);
            //将图表数据转换为BASE64编码的字符串
            reportDataMap.put("doubleMonthChart", chartGenerator.chartToBASE64(doubleMonthChart));
            reportDataMap.put("beginMonthChart", chartGenerator.chartToBASE64(beginMonthChart));
            reportDataMap.put("beginCommentChart", chartGenerator.chartToBASE64(beginCommentChart));
            reportDataMap.put("endCommentChart", chartGenerator.chartToBASE64(endCommentChart));
            reportDataMap.put("endMonthChart", chartGenerator.chartToBASE64(endMonthChart));

            return reportDataMap;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public Map<String, Object> getPostReportDataMap(int year, int issue, List<String> urls) throws Exception {
        Map<String, Object> reportDataMap = new HashMap<>();
        int beginMonth = issue;
        int endMonth = issue + 1;
        String generateDate = DateTimeFormatter.ofPattern("yyyy年MM月dd日").format(LocalDateTime.now());
        List<Post> postList;
//        List<SpecialPost> list = specialPostDao.selectAllSpecialPost();
        postList = mapToPostList(urls);
        reportDataMap.put("year", year);
        reportDataMap.put("generateDate", generateDate);
        reportDataMap.put("beginMonth", beginMonth);
        reportDataMap.put("endMonth", endMonth);
        reportDataMap.put("postCount", postList.size());
        reportDataMap.put("postList", postList);

        return reportDataMap;
    }

    //TODO
    private List<Post> mapToPostList(List<String> urls) throws IOException,NoEventException,Exception {
        List<Post> result = new ArrayList<>();
//        list.forEach(specialPost -> urls.addAll(specialPost.getUrl()));
        List<SpecialPostEventChart> list1;
        if (urls.size() >= 5) {
            list1 = specialPostEventChartService.getChartPoints(urls.subList(0, 5));
        } else {
            list1 = specialPostEventChartService.getChartPoints(urls);
        }
        for(int i = 0; i < list1.size(); i++){
            SpecialPostEventChart specialPostEventChart = list1.get(i);
            Post post = new Post();
            post.setIndex(i);
            post.setUrl(urls.get(i));
            post.setTheme(specialPostEventChart.getTheme());
            JFreeChart jFreeChart = chartGenerator.generateChart(specialPostEventChart.getChartPoint(),"帖子趋势图",ChartTypeEnum.SPECIAL_POST);
            post.setImageContent(chartGenerator.chartToBASE64(jFreeChart));
            result.add(post);
        }

        return result;
    }
}
