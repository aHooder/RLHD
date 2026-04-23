package rs117.hd.tests;

import java.util.LinkedHashMap;
import org.junit.Assert;
import org.junit.Test;
import rs117.hd.config.SeasonalTheme;
import rs117.hd.utils.VariableSupplier;

import static rs117.hd.utils.ExpressionParser.parseExpression;
import static rs117.hd.utils.ExpressionParser.parseFunction;
import static rs117.hd.utils.ExpressionParser.parsePredicate;

public class ExpressionParserTest {
	@Test
	public void testExpressionParser() {
		VariableSupplier vars = name -> {
			switch (name) {
				case "h":
					return 5;
				case "s":
					return 10;
				case "l":
					return 5;
				case "blending":
					return true;
				case "textures":
					return false;
			}
			return null;
		};

		Assert.assertEquals(5.f, parseExpression("5"));
		Assert.assertEquals(-5.f, parseExpression("-5"));
		Assert.assertEquals(-2.5f, parseExpression("-2.5"));
		Assert.assertEquals(-.5f, parseExpression("-0.5"));
		Assert.assertEquals(-.5f, parseExpression("-.5"));
		Assert.assertEquals(.5f, parseExpression(".5"));
		Assert.assertEquals(.5f, parseExpression("+.5"));
		Assert.assertEquals(.5f, parseExpression("++ +.5"));
		Assert.assertEquals(1f, parseExpression("--1"));
		Assert.assertEquals(.5f, parseExpression("+-++-.5"));
		Assert.assertEquals(17.f, parseFunction("5 + 12").apply(null));
		Assert.assertEquals(16.f, parseExpression("8 / 2 * (2 + 2)"));
		Assert.assertEquals(32.f, parseExpression("2 * 8 / 2 * (2 + 2)"));
		Assert.assertEquals(3.f, parseExpression("2 * 3 / 2"));
		Assert.assertEquals(0.f, parseExpression("2 * 8 - 4 * 4"));
		Assert.assertEquals(29.f, parseExpression("2 + 3 * (8 + 5 / 5)"));
		Assert.assertEquals(40.f, parseExpression("(8 - 1 + 3) * 6 - ((3 + 7) * 2)"));
		Assert.assertEquals(21.f, parseExpression("(1 + 2) * (3 + 4)"));
		Assert.assertFalse(parsePredicate("!( blending )").test(vars));
		Assert.assertEquals(false, parseExpression("!true"));
		Assert.assertEquals(true, parseExpression("SUMMER == 1", name -> SeasonalTheme.valueOf(name).ordinal()));

		assertThrows(() -> parseExpression("unexpected ( indeed"));
		assertThrows(() -> parseExpression("(5 + ( missing paren)"));

		LinkedHashMap<String, Boolean> testCases = new LinkedHashMap<>();
		testCases.put("h != 0", true);
		testCases.put("s == 0 || h <= 10 && s < 2", false);
		testCases.put("h == 8 && (s == 3 || s == 4) && l >= 20", false);
		testCases.put("h > 3 && s < 15 && l < 21", true);
		testCases.put("h < 3 && s < 15 && l < 21", false);
		testCases.put("h > 3 && (s < 9 || l < 19)", true);
		testCases.put("h == 5 ? s > 3 : s > 15", true);
		testCases.put("h == s || h == l", true);
		testCases.put("blending || textures", true);

		for (var entry : testCases.entrySet()) {
			var predicate = parsePredicate(entry.getKey());
			var result = predicate.test(vars);
			var passed = entry.getValue() == result;
			System.out.println(
				(passed ? "\u001B[32m" : "\u001B[31m") +
				"Case: " + entry.getKey() + " " + (passed ? "passed" : "failed") + ". Expected: " + entry.getValue() + ", got: " + result);
		}
	}

	private static void assertThrows(Runnable runnable) {
		try {
			runnable.run();
		} catch (Throwable ex) {
			System.out.println("\u001B[32m" + "Case: Threw as expected: " + ex);
			return;
		}
		Assert.fail("Didn't throw an exception");
	}

	@Test
	public void testExpressionEvaluator() {
		VariableSupplier vars = name -> {
			switch (name) {
				case "h":
					return 5;
				case "s":
					return 10;
				case "l":
					return 5;
				case "blending":
					return true;
				case "textures":
					return false;
			}
			return null;
		};

		// Basic arithmetic — results come back as Float
		Assert.assertEquals(17.f, (float) parseFunction("5 + 12").apply(null), 0);
		Assert.assertEquals(16.f, (float) parseFunction("8 / 2 * (2 + 2)").apply(null), 0);
		Assert.assertEquals(32.f, (float) parseFunction("2 * 8 / 2 * (2 + 2)").apply(null), 0);
		Assert.assertEquals(3.f, (float) parseFunction("2 * 3 / 2").apply(null), 0);
		Assert.assertEquals(0.f, (float) parseFunction("2 * 8 - 4 * 4").apply(null), 0);
		Assert.assertEquals(29.f, (float) parseFunction("2 + 3 * (8 + 5 / 5)").apply(null), 0);
		Assert.assertEquals(40.f, (float) parseFunction("(8 - 1 + 3) * 6 - ((3 + 7) * 2)").apply(null), 0);
		Assert.assertEquals(21.f, (float) parseFunction("(1 + 2) * (3 + 4)").apply(null), 0);

		// Boolean literals and NOT
		Assert.assertEquals(false, parseFunction("!true").apply(null));
		Assert.assertEquals(true, parseFunction("!false").apply(null));
		Assert.assertEquals(true, parseFunction("!!true").apply(null));

		// Float equality — both sides are numeric
		Assert.assertEquals(true, parseFunction("5 == 5").apply(null));
		Assert.assertEquals(false, parseFunction("5 == 6").apply(null));
		Assert.assertEquals(true, parseFunction("5 != 6").apply(null));
		Assert.assertEquals(false, parseFunction("5 != 5").apply(null));

		// Boolean equality — both sides are boolean literals; this is the cast that was crashing
		Assert.assertEquals(true, parseFunction("true == true").apply(null));
		Assert.assertEquals(false, parseFunction("true == false").apply(null));
		Assert.assertEquals(true, parseFunction("false != true").apply(null));
		Assert.assertEquals(false, parseFunction("true != true").apply(null));

		// Variable equality against a float — variable resolves to Integer → sanitized to Float
		Assert.assertEquals(true, parseFunction("h == 5").apply(vars));
		Assert.assertEquals(false, parseFunction("h == 6").apply(vars));
		Assert.assertEquals(true, parseFunction("h != 6").apply(vars));

		// Variable equality against a boolean — this is the mixed-type case that was crashing
		Assert.assertEquals(true, parseFunction("blending == true").apply(vars));
		Assert.assertEquals(false, parseFunction("blending == false").apply(vars));
		Assert.assertEquals(false, parseFunction("textures == true").apply(vars));
		Assert.assertEquals(true, parseFunction("textures != true").apply(vars));

		// Comparisons with variables
		Assert.assertEquals(true, parseFunction("h > 3").apply(vars));
		Assert.assertEquals(false, parseFunction("h > 5").apply(vars));
		Assert.assertEquals(true, parseFunction("h >= 5").apply(vars));
		Assert.assertEquals(false, parseFunction("h < 5").apply(vars));
		Assert.assertEquals(true, parseFunction("h <= 5").apply(vars));
		Assert.assertEquals(true, parseFunction("s > h").apply(vars));

		// Logical AND / OR with variables
		Assert.assertEquals(true, parseFunction("blending || textures").apply(vars));
		Assert.assertEquals(false, parseFunction("blending && textures").apply(vars));
		Assert.assertEquals(true, parseFunction("!textures && blending").apply(vars));

		// NOT applied to a variable
		Assert.assertEquals(false, parseFunction("!blending").apply(vars));
		Assert.assertEquals(true, parseFunction("!textures").apply(vars));

		// Ternary — condition true
		Assert.assertEquals(true, parseFunction("h == 5 ? s > 3 : s > 15").apply(vars));
		// Ternary — condition false
		Assert.assertEquals(false, parseFunction("h == 6 ? s > 3 : s > 15").apply(vars));
		// Ternary returning numeric branch
		Assert.assertEquals(10.f, (float) parseFunction("h == 5 ? s : h").apply(vars), 0);
		Assert.assertEquals(5.f, (float) parseFunction("h == 6 ? s : h").apply(vars), 0);

		// Compound expressions mirroring the predicate test cases
		Assert.assertEquals(true, parseFunction("h != 0").apply(vars));
		Assert.assertEquals(false, parseFunction("s == 0 || h <= 10 && s < 2").apply(vars));
		Assert.assertEquals(false, parseFunction("h == 8 && (s == 3 || s == 4) && l >= 20").apply(vars));
		Assert.assertEquals(true, parseFunction("h > 3 && s < 15 && l < 21").apply(vars));
		Assert.assertEquals(false, parseFunction("h < 3 && s < 15 && l < 21").apply(vars));
		Assert.assertEquals(true, parseFunction("h > 3 && (s < 9 || l < 19)").apply(vars));
		Assert.assertEquals(true, parseFunction("h == s || h == l").apply(vars));

		// Modulo
		Assert.assertEquals(1.f, (float) parseFunction("5 % 2").apply(null), 0);
		Assert.assertEquals(0.f, (float) parseFunction("4 % 2").apply(null), 0);
		Assert.assertEquals(true, parseFunction("5 % 2 == 1").apply(null));
	}
}
