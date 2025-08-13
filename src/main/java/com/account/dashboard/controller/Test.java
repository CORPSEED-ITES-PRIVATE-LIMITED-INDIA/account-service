package com.account.dashboard.controller;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Test {

	public static void main(String[] args) {
		int []a= {7,5,2,3,8,12};
		List<Integer> arr = Arrays.stream(a).boxed().sorted((c , d) -> c-d).collect(Collectors.toList());
		System.out.println(arr);

	}

}
