package com.populstay.common.log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by Jerry
 */
public class DateUtil {

	public static final String DATE_TIME_PATTERN_1 = "yyyy-MM-dd HH:mm:ss";
	public static final String DATE_TIME_PATTERN_2 = "yyyy-MM-dd";
	public static final String DATE_TIME_PATTERN_3 = "yyyy-MM-dd HH:mm";

	// 日期格式
	public static final String DATE_FORMAT_HH_00 = "HH:00";
	public static final String DATE_FORMAT_YYYY_MM_DD = "yyyy-MM-dd";
	public static final String DATE_FORMAT_YYYY_MM_DD_HH = "yyyy-MM-dd HH";
	public static final String DATE_FORMAT_YYYY_MM_DD_HH_00 = "yyyy-MM-dd HH:00";
	public static final String DATE_FORMAT_YYYY_MM_DD_HH_MM = "yyyy-MM-dd HH:mm";
	public static final String DATE_FORMAT_YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

	/**
	 * 获取当前时间
	 *
	 * @param pattern
	 * @return
	 */
	public static String getCurDate(String pattern) {
		SimpleDateFormat sDateFormat = new SimpleDateFormat(pattern);
		return sDateFormat.format(new Date());
	}

	/**
	 * 时间戳转换成字符串
	 *
	 * @param second 需要乘以1000
	 * @param pattern
	 * @return
	 */
	public static String getDateToStringConvert(long second, String pattern) {
		Date date = new Date(second * 1000L);
		SimpleDateFormat format = new SimpleDateFormat(pattern);
		return format.format(date);
	}

	public static String getDateToString(long milSecond, String pattern) {
		Date date = new Date(milSecond);
		SimpleDateFormat format = new SimpleDateFormat(pattern);
		return format.format(date);
	}


	/**
	 * 将字符串转为时间戳
	 *
	 * @param dateString
	 * @param pattern
	 * @return
	 */
	public static long getStringToDate(String dateString, String pattern) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
		Date date = new Date();
		try {
			date = dateFormat.parse(dateString);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return date.getTime();
	}

	/**
	 * 日期转换成字符串
	 *
	 * @param date
	 * @param pattern
	 * @return
	 */
	public static String getDateToString(Date date, String pattern) {
		SimpleDateFormat format = new SimpleDateFormat(pattern);
		return format.format(date);
	}

	/**
	 * 获取系统时间戳
	 */
	public static long getCurTimeMillis() {
		return System.currentTimeMillis();
	}

	/**
	 * 获取当前时区
	 */
	public static int getTimeZone() {
		TimeZone tz = TimeZone.getDefault();
		int rawOffset = tz.getRawOffset();
		return rawOffset / 60 / 60 / 1000;// 时区，东时区数字为正，西时区为负
	}

	/**
	 * 获取当前时区的偏移时间
	 */
	public static long getTimeZoneOffset() {
		TimeZone tz = TimeZone.getDefault();
		return (long) tz.getOffset(System.currentTimeMillis());
	}
}

