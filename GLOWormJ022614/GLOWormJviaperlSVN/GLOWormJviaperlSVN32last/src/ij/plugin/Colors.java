package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.filter.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/** This plugin implements most of the Edit/Options/Colors command. */
public class Colors implements PlugIn, ItemListener {
	public static final String[] colors = {"red","green","blue","magenta","cyan","yellow","orange","black","white"};
	private static final String[] colors2 = {"Red","Green","Blue","Magenta","Cyan","Yellow","Orange","Black","White"};
	private Choice fchoice, bchoice, schoice;
	private Color fc2, bc2, sc2;
	private static Hashtable<String,String> kaleidoColors;
	
	public static void init() {
		kaleidoColors = new Hashtable<String,String>();
		kaleidoColors.put("plum","#ffe64cff");
		kaleidoColors.put("royal","#ff66e6ff");
		kaleidoColors.put("azure","#ffccccff");
		kaleidoColors.put("pink","#ffffcccc");
		kaleidoColors.put("spring","#ffccffcc");
		kaleidoColors.put("wisteria","#ffffccff");
		kaleidoColors.put("sunset","#ffffcce6");
		kaleidoColors.put("turquoise","#ffccffff");
		kaleidoColors.put("butter","#ffffffcc");

		String bigColorList =		
				"IndianRed,#CD5C5C\n"+
						"LightCoral,#F08080\n"+
						"Salmon,#FA8072\n"+
						"DarkSalmon,#E9967A\n"+
						"LightSalmon,#FFA07A\n"+
						"Crimson,#DC143C\n"+
						"Red,#FF0000\n"+
						"FireBrick,#B22222\n"+
						"DarkRed,#8B0000\n"+
						"Pink,#FFC0CB\n"+
						"LightPink,#FFB6C1\n"+
						"HotPink,#FF69B4\n"+
						"DeepPink,#FF1493\n"+
						"MediumVioletRed,#C71585\n"+
						"PaleVioletRed,#DB7093\n"+
						"LightSalmon,#FFA07A\n"+
						"Coral,#FF7F50\n"+
						"Tomato,#FF6347\n"+
						"OrangeRed,#FF4500\n"+
						"DarkOrange,#FF8C00\n"+
						"Orange,#FFA500\n"+
						"Gold,#FFD700\n"+
						"Yellow,#FFFF00\n"+
						"LightYellow,#FFFFE0\n"+
						"LemonChiffon,#FFFACD\n"+
						"LightGoldenrodYellow,#FAFAD2\n"+
						"PapayaWhip,#FFEFD5\n"+
						"Moccasin,#FFE4B5\n"+
						"PeachPuff,#FFDAB9\n"+
						"PaleGoldenrod,#EEE8AA\n"+
						"Khaki,#F0E68C\n"+
						"DarkKhaki,#BDB76B\n"+
						"Lavender,#E6E6FA\n"+
						"Thistle,#D8BFD8\n"+
						"Plum,#DDA0DD\n"+
						"Violet,#EE82EE\n"+
						"Orchid,#DA70D6\n"+
						"Fuchsia,#FF00FF\n"+
						"Magenta,#FF00FF\n"+
						"MediumOrchid,#BA55D3\n"+
						"MediumPurple,#9370DB\n"+
						"Amethyst,#9966CC\n"+
						"BlueViolet,#8A2BE2\n"+
						"DarkViolet,#9400D3\n"+
						"DarkOrchid,#9932CC\n"+
						"DarkMagenta,#8B008B\n"+
						"Purple,#800080\n"+
						"Indigo,#4B0082\n"+
						"SlateBlue,#6A5ACD\n"+
						"DarkSlateBlue,#483D8B\n"+
						"MediumSlateBlue,#7B68EE\n"+
						"ColorName,#HexCode\n"+
						"GreenYellow,#ADFF2F\n"+
						"Chartreuse,#7FFF00\n"+
						"LawnGreen,#7CFC00\n"+
						"Lime,#00FF00\n"+
						"LimeGreen,#32CD32\n"+
						"PaleGreen,#98FB98\n"+
						"LightGreen,#90EE90\n"+
						"MediumSpringGreen,#00FA9A\n"+
						"SpringGreen,#00FF7F\n"+
						"MediumSeaGreen,#3CB371\n"+
						"SeaGreen,#2E8B57\n"+
						"ForestGreen,#228B22\n"+
						"Green,#008000\n"+
						"DarkGreen,#006400\n"+
						"YellowGreen,#9ACD32\n"+
						"OliveDrab,#6B8E23\n"+
						"Olive,#808000\n"+
						"DarkOliveGreen,#556B2F\n"+
						"MediumAquamarine,#66CDAA\n"+
						"DarkSeaGreen,#8FBC8F\n"+
						"LightSeaGreen,#20B2AA\n"+
						"DarkCyan,#008B8B\n"+
						"Teal,#008080\n"+
						"Aqua,#00FFFF\n"+
						"Cyan,#00FFFF\n"+
						"LightCyan,#E0FFFF\n"+
						"PaleTurquoise,#AFEEEE\n"+
						"Aquamarine,#7FFFD4\n"+
						"Turquoise,#40E0D0\n"+
						"MediumTurquoise,#48D1CC\n"+
						"DarkTurquoise,#00CED1\n"+
						"CadetBlue,#5F9EA0\n"+
						"SteelBlue,#4682B4\n"+
						"LightSteelBlue,#B0C4DE\n"+
						"PowderBlue,#B0E0E6\n"+
						"LightBlue,#ADD8E6\n"+
						"SkyBlue,#87CEEB\n"+
						"LightSkyBlue,#87CEFA\n"+
						"DeepSkyBlue,#00BFFF\n"+
						"DodgerBlue,#1E90FF\n"+
						"CornflowerBlue,#6495ED\n"+
						"MediumSlateBlue,#7B68EE\n"+
						"RoyalBlue,#4169E1\n"+
						"Blue,#0000FF\n"+
						"MediumBlue,#0000CD\n"+
						"DarkBlue,#00008B\n"+
						"Navy,#000080\n"+
						"MidnightBlue,#191970\n"+
						"Cornsilk,#FFF8DC\n"+
						"BlanchedAlmond,#FFEBCD\n"+
						"Bisque,#FFE4C4\n"+
						"NavajoWhite,#FFDEAD\n"+
						"Wheat,#F5DEB3\n"+
						"BurlyWood,#DEB887\n"+
						"Tan,#D2B48C\n"+
						"RosyBrown,#BC8F8F\n"+
						"SandyBrown,#F4A460\n"+
						"Goldenrod,#DAA520\n"+
						"DarkGoldenrod,#B8860B\n"+
						"Peru,#CD853F\n"+
						"Chocolate,#D2691E\n"+
						"SaddleBrown,#8B4513\n"+
						"Sienna,#A0522D\n"+
						"Brown,#A52A2A\n"+
						"Maroon,#800000\n"+
						"White,#FFFFFF\n"+
						"Snow,#FFFAFA\n"+
						"Honeydew,#F0FFF0\n"+
						"MintCream,#F5FFFA\n"+
						"Azure,#F0FFFF\n"+
						"AliceBlue,#F0F8FF\n"+
						"GhostWhite,#F8F8FF\n"+
						"WhiteSmoke,#F5F5F5\n"+
						"Seashell,#FFF5EE\n"+
						"Beige,#F5F5DC\n"+
						"OldLace,#FDF5E6\n"+
						"FloralWhite,#FFFAF0\n"+
						"Ivory,#FFFFF0\n"+
						"AntiqueWhite,#FAEBD7\n"+
						"Linen,#FAF0E6\n"+
						"LavenderBlush,#FFF0F5\n"+
						"MistyRose,#FFE4E1\n"+
						"Gainsboro,#DCDCDC\n"+
						"LightGrey,#D3D3D3\n"+
						"Silver,#C0C0C0\n"+
						"DarkGray,#A9A9A9\n"+
						"Gray,#808080\n"+
						"DimGray,#696969\n"+
						"LightSlateGray,#778899\n"+
						"SlateGray,#708090\n"+
						"DarkSlateGray,#2F4F4F\n"+
						"Black,#000000\n"+ 
						"black,#000000\n"+
						"midnightblue2,#000033\n"+
						"navy,#000080\n"+
						"darkblue,#00008B\n"+
						"newmidnightblue,#00009C\n"+
						"mediumblue,#0000CD\n"+
						"blue2,#0000EE\n"+
						"blue,#0000FF\n"+
						"pinegreen,#003300\n"+
						"cichlid,#003EFF\n"+
						"signblue,#003F87\n"+
						"dumpster,#004F00\n"+
						"celtics,#00611C\n"+
						"darkgreen,#006400\n"+
						"starbucks,#006633\n"+
						"deepskyblue4,#00688B\n"+
						"signgreen,#006B54\n"+
						"slateblue,#007FFF\n"+
						"green,#008000\n"+
						"teal,#008080\n"+
						"turquoise4,#00868B\n"+
						"green4,#008B00\n"+
						"springgreen4,#008B45\n"+
						"darkcyan,#008B8B\n"+
						"irishflag,#009900\n"+
						"skyblue5,#0099CC\n"+
						"deepskyblue3,#009ACD\n"+
						"truegreen,#00AF33\n"+
						"deepskyblue2,#00B2EE\n"+
						"deepskyblue,#00BFFF\n"+
						"turquoise3,#00C5CD\n"+
						"turquoiseblue,#00C78C\n"+
						"emeraldgreen,#00C957\n"+
						"green3,#00CD00\n"+
						"springgreen3,#00CD66\n"+
						"cyan3,#00CDCD\n"+
						"darkturquoise,#00CED1\n"+
						"turquoise2,#00E5EE\n"+
						"green2,#00EE00\n"+
						"springgreen2,#00EE76\n"+
						"cyan2,#00EEEE\n"+
						"turquoise1,#00F5FF\n"+
						"mediumspringgreen,#00FA9A\n"+
						"lime,#00FF00\n"+
						"springgreen,#00FF33\n"+
						"neonavocado,#00FF66\n"+
						"springgreen,#00FF7F\n"+
						"seagreen,#00FFAA\n"+
						"lightteal,#00FFCC\n"+
						"aqua,#00FFFF\n"+
						"cyan,#00FFFF\n"+
						"tynant,#0147FA\n"+
						"topaz,#0198E1\n"+
						"mouthwash,#01C5BB\n"+
						"picassoblue,#0276FD\n"+
						"gray1,#030303\n"+
						"manganeseblue,#03A89E\n"+
						"gray2,#050505\n"+
						"cerulean,#05B8CC\n"+
						"indiglo,#05E9FF\n"+
						"tealLED,#05EDFF\n"+
						"seagreen,#068481\n"+
						"gray3,#080808\n"+
						"gray4,#0A0A0A\n"+
						"permanentgreen,#0AC92B\n"+
						"policestrobe,#0BB5FF\n"+
						"gray5,#0D0D0D\n"+
						"indigodye,#0D4F8B\n"+
						"banker'slamp,#0E8C3A\n"+
						"diamondblue,#0EBFE9\n"+
						"gray6,#0F0F0F\n"+
						"turquoise,#0FDDAF\n"+
						"dodgerblue4,#104E8B\n"+
						"malachite,#108070\n"+
						"ultramarine,#120A8F\n"+
						"gray7,#121212\n"+
						"gardenhose,#138F6A\n"+
						"gray8,#141414\n"+
						"ulyssesbutterfly,#1464F4\n"+
						"bluevelvet,#162252\n"+
						"gray9,#171717\n"+
						"mastersjacket,#174038\n"+
						"dodgerblue3,#1874CD\n"+
						"midnightblue,#191970\n"+
						"gray10,#1A1A1A\n"+
						"alaskasky,#1B3F8B\n"+
						"6ball,#1B6453\n"+
						"gray11,#1C1C1C\n"+
						"dodgerblue2,#1C86EE\n"+
						"peafowl,#1D7CF2\n"+
						"bottlegreen,#1DA237\n"+
						"dodgerblue,#1E90FF\n"+
						"gray12,#1F1F1F\n"+
						"lightseagreen,#20B2AA\n"+
						"emeraldcity,#20BF9F\n"+
						"gray13,#212121\n"+
						"packergreen,#213D30\n"+
						"huntergreen,#215E21\n"+
						"indigo2,#218868\n"+
						"delft,#22316C\n"+
						"forestgreen,#228B22\n"+
						"navyblue,#23238E\n"+
						"steelblue,#236B8E\n"+
						"seagreen,#238E68\n"+
						"gray14,#242424\n"+
						"gummigreen,#24D330\n"+
						"gray15,#262626\n"+
						"bluespider,#26466D\n"+
						"royalblue4,#27408B\n"+
						"pabstblue,#283A90\n"+
						"cooler,#284942\n"+
						"emeraldgreen2,#28AE7B\n"+
						"ivoryblack,#292421\n"+
						"gray16,#292929\n"+
						"atlanticgreen,#2A8E82\n"+
						"gray17,#2B2B2B\n"+
						"blueangels,#2B4F81\n"+
						"stlouisblues,#2C5197\n"+
						"cucumber,#2C5D3F\n"+
						"indigo,#2E0854\n"+
						"gray18,#2E2E2E\n"+
						"stainedglass,#2E37FE\n"+
						"lampblack,#2E473B\n"+
						"parkbench,#2E6444\n"+
						"seagreen,#2E8B57\n"+
						"midnightblue,#2F2F4F\n"+
						"darkgreen,#2F4F2F\n"+
						"darkslategrey,#2F4F4F\n"+
						"darkslategray,#2F4F4F\n"+
						"aquarium,#2FAA96\n"+
						"presidentialblue,#302B54\n"+
						"gray19,#303030\n"+
						"mailbox,#3063A5\n"+
						"sapgreen,#308014\n"+
						"pooltable,#31B94D\n"+
						"mediumblue,#3232CC\n"+
						"mediumblue2,#3232CD\n"+
						"limerind,#324F17\n"+
						"lizeyes,#325C74\n"+
						"armymen,#327556\n"+
						"greenline,#329555\n"+
						"skyblue6,#3299CC\n"+
						"mediumaquamarine2,#32CC99\n"+
						"limegreen,#32CD32\n"+
						"mediumaquamarine3,#32CD99\n"+
						"darkcherryred,#330000\n"+
						"cornflower,#3300FF\n"+
						"gray20,#333333\n"+
						"royalblue,#3333FF\n"+
						"oldmoney,#337147\n"+
						"peacock,#33A1C9\n"+
						"blueline,#33A1DE\n"+
						"parrotgreen,#33FF33\n"+
						"bluecorn,#344152\n"+
						"octopus,#34925E\n"+
						"armyuniform,#353F3E\n"+
						"pacificblue,#35586C\n"+
						"parrot,#3579DC\n"+
						"cafeamericano,#362819\n"+
						"gray21,#363636\n"+
						"steelblue4,#36648B\n"+
						"pacificgreen,#36DBCA\n"+
						"aquaman,#37BC61\n"+
						"metallicmint,#37FDFC\n"+
						"bluedeep,#380474\n"+
						"gray22,#383838\n"+
						"terreverte,#385E0F\n"+
						"sgiteal,#388E8E\n"+
						"summersky,#38B0DE\n"+
						"greengrassofhome,#395D33\n"+
						"greenpepper,#397D02\n"+
						"nypdblue,#39B7CD\n"+
						"blackberry,#3A3A38\n"+
						"bluetrain,#3A5894\n"+
						"royalblue3,#3A5FCD\n"+
						"circuitboard,#3A6629\n"+
						"bigbluebus,#3A66A7\n"+
						"dressblue,#3B3178\n"+
						"gray23,#3B3B3B\n"+
						"bluegrapes,#3B4990\n"+
						"romainelettuce,#3B5323\n"+
						"olive3b,#3B5E2B\n"+
						"greekroof,#3B6AA0\n"+
						"bluegreenalgae,#3B8471\n"+
						"mediumseagreen,#3CB371\n"+
						"gray24,#3D3D3D\n"+
						"wetmoss,#3D5229\n"+
						"cobalt,#3D59AB\n"+
						"obsidian,#3D5B43\n"+
						"greenlantern,#3D8B37\n"+
						"cobaltgreen,#3D9140\n"+
						"greenparty,#3E6B4F\n"+
						"greengables,#3E766D\n"+
						"mediterranean,#3E766D\n"+
						"ooze,#3E7A5E\n"+
						"clover,#3EA055\n"+
						"douglasfir,#3F602B\n"+
						"royalpalm,#3F6826\n"+
						"greentaxi,#3F9E4D\n"+
						"gray25,#404040\n"+
						"shamrock,#40664D\n"+
						"turquoise,#40E0D0\n"+
						"jackpine,#414F12\n"+
						"royalblue,#4169E1\n"+
						"blackbeautyplum,#422C2F\n"+
						"gray26,#424242\n"+
						"cornflowerblue,#42426F\n"+
						"bluejeans,#42526C\n"+
						"isleroyalegreenstone,#426352\n"+
						"bluewhale,#42647F\n"+
						"mediumseagreen,#426F42\n"+
						"caribbean,#42C0FB\n"+
						"spinach,#435D36\n"+
						"royalblue2,#436EEE\n"+
						"denim,#4372AA\n"+
						"seagreen3,#43CD80\n"+
						"go,#43D58C\n"+
						"gray27,#454545\n"+
						"greenagate,#457371\n"+
						"chartreuse4,#458B00\n"+
						"aquamarine4,#458B74\n"+
						"naturalturquoise,#45C3B8\n"+
						"odgreen,#46523C\n"+
						"steelblue,#4682B4\n"+
						"slateblue4,#473C8B\n"+
						"gray28,#474747\n"+
						"noblefir,#476A34\n"+
						"darkslateblue,#483D8B\n"+
						"parkranger,#484D46\n"+
						"scotlandpound,#487153\n"+
						"royalblue1,#4876FF\n"+
						"holly,#488214\n"+
						"mediumturquoise,#48D1CC\n"+
						"bluebird,#4973AB\n"+
						"blueridgemtns,#4981CE\n"+
						"bluebucket,#499DF5\n"+
						"Nerfgreen,#49E20E\n"+
						"electricturquoise,#49E9BD\n"+
						"gray29,#4A4A4A\n"+
						"kakapo,#4A7023\n"+
						"skyblue4,#4A708B\n"+
						"darkgreencopper,#4A766E\n"+
						"fenwaymonster,#4A777A\n"+
						"wales,#4AC948\n"+
						"indigo,#4B0082\n"+
						"greenMM,#4BB74C\n"+
						"bluegrass,#4C7064\n"+
						"bluelagoon,#4CB7A5\n"+
						"kelly,#4CBB17\n"+
						"gray30,#4D4D4D\n"+
						"neonblue,#4D4DFF\n"+
						"fraserfir,#4D6B50\n"+
						"pollockblue,#4D6FAC\n"+
						"lakeontario,#4D71A3\n"+
						"greenvisor,#4D7865\n"+
						"grass,#4DBD33\n"+
						"aquamarine,#4E78A0\n"+
						"seagreen2,#4EEE94\n"+
						"violet,#4F2F4F\n"+
						"darkolivegreen,#4F4F2F\n"+
						"gray31,#4F4F4F\n"+
						"greenscrubs,#4F8E83\n"+
						"steelblue3,#4F94CD\n"+
						"lakesuperior,#506987\n"+
						"bluestone,#50729F\n"+
						"LCDdark,#507786\n"+
						"lakemichigan,#50A6C2\n"+
						"maltablue,#517693\n"+
						"greenstamp,#517B58\n"+
						"bluepill,#5190ED\n"+
						"gray32,#525252\n"+
						"bluedog,#525C65\n"+
						"fenwaygrass,#526F35\n"+
						"greencopper,#527F76\n"+
						"darkslategray4,#528B8B\n"+
						"cadetblue4,#53868B\n"+
						"carolinablue,#539DC2\n"+
						"grape,#543948\n"+
						"watermelonrind,#54632C\n"+
						"palegreen4,#548B54\n"+
						"seagreen1,#54FF9F\n"+
						"burntsienna,#551011\n"+
						"deeppurple,#551033\n"+
						"passionfruit,#55141C\n"+
						"purple4,#551A8B\n"+
						"gray33,#555555\n"+
						"darkolivegreen,#556B2F\n"+
						"leaf,#55AE3A\n"+
						"forestgreen2,#567E3A\n"+
						"gray34,#575757\n"+
						"mtndewbottle,#577A3A\n"+
						"bluecornchips,#584E56\n"+
						"broccoli,#586949\n"+
						"gray35,#595959\n"+
						"richblue,#5959AB\n"+
						"snake,#596C56\n"+
						"blueberryfresh,#5971AD\n"+
						"greenbark,#597368\n"+
						"chemicalsuit,#5993E5\n"+
						"lizard,#5A6351\n"+
						"curacao,#5B59BA\n"+
						"naturalgas,#5B90F6\n"+
						"emerald,#5B9C64\n"+
						"ultramarineviolet,#5C246E\n"+
						"bakerschocolate,#5C3317\n"+
						"verydarkbrown,#5C4033\n"+
						"gray36,#5C5C5C\n"+
						"steelblue2,#5CACEE\n"+
						"mediumpurple4,#5D478B\n"+
						"lakehuron,#5D7B93\n"+
						"bluesponge,#5D92B1\n"+
						"greenLED,#5DFC0A\n"+
						"vandykebrown,#5E2605\n"+
						"sepia,#5E2612\n"+
						"purplerose,#5E2D79\n"+
						"gray37,#5E5E5E\n"+
						"freshgreen,#5EDA9E\n"+
						"tealeaves,#5F755E\n"+
						"cadetblue,#5F9EA0\n"+
						"cadetblue,#5F9F9F\n"+
						"signbrown,#603311\n"+
						"lightskyblue4,#607B8B\n"+
						"fisherman'sfloat,#607C6E\n"+
						"palm,#608341\n"+
						"lamaisonbleue,#60AFFE\n"+
						"tank,#615E3F\n"+
						"gray38,#616161\n"+
						"lakeerie,#6183A6\n"+
						"cinnabargreen,#61B329\n"+
						"greenapple,#629632\n"+
						"tropicalblue,#62B1F6\n"+
						"gray39,#636363\n"+
						"cactus,#636F57\n"+
						"greenalgae,#63AB62\n"+
						"steelblue1,#63B8FF\n"+
						"surf,#63D1F4\n"+
						"seaweed,#646F5E\n"+
						"cornflowerblue,#6495ED\n"+
						"lindsayeyes,#65909A\n"+
						"treemoss,#659D32\n"+
						"bloodred,#660000\n"+
						"bluesafe,#6600FF\n"+
						"concordgrape,#660198\n"+
						"gray40,#666666\n"+
						"cobalt,#6666FF\n"+
						"chromeoxidegreen,#668014\n"+
						"paleturquoise4,#668B8B\n"+
						"greenash,#668E86\n"+
						"aqua,#66CCCC\n"+
						"chartreuse3,#66CD00\n"+
						"mediumaquamarine,#66CDAA\n"+
						"wasabi,#66FF66\n"+
						"neonblue,#67C8FF\n"+
						"swimmingpool,#67E6EC\n"+
						"darkorchid4,#68228B\n"+
						"seaurchin,#683A5E\n"+
						"bluetuna,#687C97\n"+
						"pondscum,#687E5A\n"+
						"lightblue4,#68838B\n"+
						"englandpound,#688571\n"+
						"maroon5,#691F01\n"+
						"purplerain,#694489\n"+
						"slateblue3,#6959CD\n"+
						"dimgray,#696969\n"+
						"dimgrey,#696969\n"+
						"olivedrab4,#698B22\n"+
						"darkseagreen4,#698B69\n"+
						"blueshark,#6996AD\n"+
						"putting,#699864\n"+
						"slateblue,#6A5ACD\n"+
						"greenhornet,#6A8455\n"+
						"darkslateblue,#6B238E\n"+
						"semisweetchocolate1,#6B4226\n"+
						"gray42,#6B6B6B\n"+
						"olivedrab,#6B8E23\n"+
						"slategray4,#6C7B8B\n"+
						"skyblue3,#6CA6CD\n"+
						"neptune,#6D9BF1\n"+
						"gray43,#6E6E6E\n"+
						"lightsteelblue4,#6E7B8B\n"+
						"darkolivegreen4,#6E8B3D\n"+
						"viridianlight,#6EFF70\n"+
						"salmon5,#6F4242\n"+
						"dolphin,#6F7285\n"+
						"gray44,#707070\n"+
						"slategray,#708090\n"+
						"slategrey,#708090\n"+
						"darkturquoise,#7093DB\n"+
						"aquamarine,#70DB93\n"+
						"mediumturquoise,#70DBDB\n"+
						"gardenplum,#71637D\n"+
						"sgislateblue,#7171C6\n"+
						"sgichartreuse,#71C671\n"+
						"indigotile,#72587F\n"+
						"deepochre,#733D1A\n"+
						"rawumber,#734A12\n"+
						"gray45,#737373\n"+
						"seuratblue,#739AC5\n"+
						"oldcopper,#73B1B7\n"+
						"seaweedroll,#748269\n"+
						"blueice,#74BBFB\n"+
						"lavenderfield,#754C78\n"+
						"gray46,#757575\n"+
						"bluefern,#759B84\n"+
						"blueberry,#75A1D0\n"+
						"chartreuse2,#76EE00\n"+
						"aquamarine2,#76EEC6\n"+
						"ganegreen,#777733\n"+
						"lightslategrey,#778899\n"+
						"lightslategray,#778899\n"+
						"greengoo,#77896C\n"+
						"gray47,#787878\n"+
						"pumice,#78A489\n"+
						"pea,#78AB46\n"+
						"pea,#79973F\n"+
						"Cokebottle,#79A888\n"+
						"darkslategray3,#79CDCD\n"+
						"mediumorchid4,#7A378B\n"+
						"slateblue2,#7A67EE\n"+
						"gray48,#7A7A7A\n"+
						"lightcyan4,#7A8B8B\n"+
						"cateye,#7AA9DD\n"+
						"cadetblue3,#7AC5CD\n"+
						"cinnamon,#7B3F00\n"+
						"mediumslateblue,#7B68EE\n"+
						"pickle,#7B7922\n"+
						"greenmoth,#7BBF6A\n"+
						"nightvision,#7BCC70\n"+
						"palegreen3,#7CCD7C\n"+
						"lawngreen,#7CFC00\n"+
						"purple3,#7D26CD\n"+
						"gray49,#7D7D7D\n"+
						"bluenile,#7D7F94\n"+
						"sgilightblue,#7D9EC0\n"+
						"forgetmenots,#7EB6FF\n"+
						"skyblue2,#7EC0EE\n"+
						"mediumslateblue2,#7F00FF\n"+
						"gray50,#7F7F7F\n"+
						"flightjacket,#7F8778\n"+
						"kiwi,#7F9A65\n"+
						"chartreuse,#7FFF00\n"+
						"aquamarine,#7FFFD4\n"+
						"maroon,#800000\n"+
						"purple,#800080\n"+
						"brown,#802A2A\n"+
						"olive,#808000\n"+
						"warmgrey,#808069\n"+
						"grey,#808080\n"+
						"gray,#808080\n"+
						"coldgrey,#808A87\n"+
						"eggplant,#816687\n"+
						"wildviolet,#820BBB\n"+
						"gray51,#828282\n"+
						"bluemist,#82CFFD\n"+
						"slateblue1,#836FFF\n"+
						"honeydew4,#838B83\n"+
						"azure4,#838B8B\n"+
						"nikkoblue,#838EDE\n"+
						"neongreen,#83F52C\n"+
						"lightslateblue,#8470FF\n"+
						"frog,#84BE6A\n"+
						"darkwood,#855E42\n"+
						"dustyrose,#856363\n"+
						"gray52,#858585\n"+
						"breadfruit,#859C27\n"+
						"plumpudding,#862A51\n"+
						"100euro,#86C67C\n"+
						"darkpurple,#871F78\n"+
						"raspberry,#872657\n"+
						"brownochre,#87421F\n"+
						"gray53,#878787\n"+
						"skyblue,#87CEEB\n"+
						"lightskyblue,#87CEFA\n"+
						"skyblue1,#87CEFF\n"+
						"bluecow,#88ACE0\n"+
						"mediumpurple3,#8968CD\n"+
						"blueviolet,#8A2BE2\n"+
						"burntumber,#8A3324\n"+
						"burntsienna,#8A360F\n"+
						"gray54,#8A8A8A\n"+
						"greenquartz,#8AA37B\n"+
						"darkred,#8B0000\n"+
						"darkmagenta,#8B008B\n"+
						"deeppink4,#8B0A50\n"+
						"firebrick4,#8B1A1A\n"+
						"maroon4,#8B1C62\n"+
						"violetred4,#8B2252\n"+
						"brown4,#8B2323\n"+
						"brown1,#8B2500\n"+
						"tomato4,#8B3626\n"+
						"indianred4,#8B3A3A\n"+
						"hotpink4,#8B3A62\n"+
						"coral4,#8B3E2F\n"+
						"darkorange4,#8B4500\n"+
						"saddlebrown,#8B4513\n"+
						"sienna4,#8B4726\n"+
						"palevioletred4,#8B475D\n"+
						"orchid4,#8B4789\n"+
						"salmon4,#8B4C39\n"+
						"lightsalmon4,#8B5742\n"+
						"orange4,#8B5A00\n"+
						"tan4,#8B5A2B\n"+
						"lightpink4,#8B5F65\n"+
						"pink4,#8B636C\n"+
						"darkgoldenrod4,#8B6508\n"+
						"plum4,#8B668B\n"+
						"goldenrod4,#8B6914\n"+
						"rosybrown4,#8B6969\n"+
						"burlywood4,#8B7355\n"+
						"gold4,#8B7500\n"+
						"peachpuff4,#8B7765\n"+
						"navajowhite4,#8B795E\n"+
						"thistle4,#8B7B8B\n"+
						"bisque4,#8B7D6B\n"+
						"mistyrose4,#8B7D7B\n"+
						"wheat4,#8B7E66\n"+
						"lightgoldenrod4,#8B814C\n"+
						"antiquewhite4,#8B8378\n"+
						"lavenderblush4,#8B8386\n"+
						"khaki4,#8B864E\n"+
						"seashell4,#8B8682\n"+
						"cornsilk4,#8B8878\n"+
						"lemonchiffon4,#8B8970\n"+
						"snow4,#8B8989\n"+
						"yellow4,#8B8B00\n"+
						"lightyellow4,#8B8B7A\n"+
						"ivory4,#8B8B83\n"+
						"martiniolive,#8BA446\n"+
						"soylentgreen,#8BA870\n"+
						"scarlet,#8C1717\n"+
						"bronze,#8C7853\n"+
						"gray55,#8C8C8C\n"+
						"greensoap,#8CDD81\n"+
						"lightskyblue3,#8DB6CD\n"+
						"darkslategray2,#8DEEEE\n"+
						"firebrick5,#8E2323\n"+
						"maroon6,#8E236B\n"+
						"sgibeet,#8E388E\n"+
						"sienna,#8E6B23\n"+
						"sgiolivedrab,#8E8E38\n"+
						"cadetblue2,#8EE5EE\n"+
						"violet,#8F5E99\n"+
						"gray56,#8F8F8F\n"+
						"lightsteelblue,#8F8FBC\n"+
						"greencheese,#8FA880\n"+
						"darkseagreen,#8FBC8F\n"+
						"lightblue,#8FD8D8\n"+
						"lightgreen,#90EE90\n"+
						"coolmint,#90FEFB\n"+
						"cobaltvioletdeep,#91219E\n"+
						"purple2,#912CEE\n"+
						"gray57,#919191\n"+
						"LCDback,#91B49C\n"+
						"pastelgreen,#92CCA6\n"+
						"mediumpurple,#9370DB\n"+
						"greenyellow,#93DB70\n"+
						"darkviolet,#9400D3\n"+
						"gray58,#949494\n"+
						"marsorange,#964514\n"+
						"gray59,#969696\n"+
						"etonblue,#96C8A2\n"+
						"paleturquoise3,#96CDCD\n"+
						"darktan,#97694F\n"+
						"darkslategray1,#97FFFF\n"+
						"avocado,#98A148\n"+
						"cadetblue1,#98F5FF\n"+
						"palegreen,#98FB98\n"+
						"truepurple,#990099\n"+
						"bordeaux,#99182C\n"+
						"darkorchid,#9932CC\n"+
						"darkorchid,#9932CD\n"+
						"chocolate,#993300\n"+
						"gray60,#999999\n"+
						"yellowgreen2,#99CC32\n"+
						"wavecrest,#99CDC9\n"+
						"darkorchid3,#9A32CD\n"+
						"lightblue3,#9AC0CD\n"+
						"yellowgreen,#9ACD32\n"+
						"palegreen1,#9AFF9A\n"+
						"purple1,#9B30FF\n"+
						"ceruleanblue,#9BC4E2\n"+
						"darkseagreen3,#9BCD9B\n"+
						"brick,#9C661F\n"+
						"purpleink,#9C6B98\n"+
						"gray61,#9C9C9C\n"+
						"cantaloupe,#9CA998\n"+
						"mintcandy,#9CBA7F\n"+
						"jollygreen,#9CCB19\n"+
						"reddeliciousapple,#9D1309\n"+
						"amethyst,#9D6B84\n"+
						"canvas,#9D8851\n"+
						"camo3,#9DB68C\n"+
						"burgundy,#9E0508\n"+
						"gray62,#9E9E9E\n"+
						"blueviolet,#9F5F9F\n"+
						"latte,#9F703A\n"+
						"mediumpurple2,#9F79EE\n"+
						"khaki,#9F9F5F\n"+
						"slategray3,#9FB6CD\n"+
						"purple,#A020F0\n"+
						"bingcherry,#A02422\n"+
						"sienna,#A0522D\n"+
						"gray63,#A1A1A1\n"+
						"smyrnapurple,#A2627A\n"+
						"lightsteelblue3,#A2B5CD\n"+
						"kermit,#A2BC13\n"+
						"avacado,#A2C257\n"+
						"sweetpotatovine,#A2C93A\n"+
						"darkolivegreen3,#A2CD5A\n"+
						"beigedark,#A39480\n"+
						"gray64,#A3A3A3\n"+
						"20pound,#A46582\n"+
						"lightskyblue2,#A4D3EE\n"+
						"liberty,#A4DCD1\n"+
						"brown,#A52A2A\n"+
						"bunnyeye,#A5435C\n"+
						"brown,#A62A2A\n"+
						"bronzeii,#A67D3D\n"+
						"mediumwood,#A68064\n"+
						"gray65,#A6A6A6\n"+
						"guacamole,#A6D785\n"+
						"turnip,#A74CAB\n"+
						"sandstone,#A78D84\n"+
						"gray66,#A8A8A8\n"+
						"darkgrey,#A9A9A9\n"+
						"darkgray,#A9A9A9\n"+
						"aluminum,#A9ACB6\n"+
						"camo2,#A9C9A4\n"+
						"purple6,#AA00FF\n"+
						"coffee,#AA5303\n"+
						"cinnamon,#AA6600\n"+
						"sgilightgray,#AAAAAA\n"+
						"periwinkle,#AAAAFF\n"+
						"goldgreen,#AADD00\n"+
						"mediumpurple1,#AB82FF\n"+
						"gray67,#ABABAB\n"+
						"organictea,#AC7F24\n"+
						"gray68,#ADADAD\n"+
						"lightblue,#ADD8E6\n"+
						"turquoise,#ADEAEA\n"+
						"greenyellow,#ADFF2F\n"+
						"wasabisauce,#AEBB51\n"+
						"paleturquoise2,#AEEEEE\n"+
						"signred,#AF1E2D\n"+
						"cola,#AF4035\n"+
						"paleturquoise,#AFEEEE\n"+
						"indianred,#B0171F\n"+
						"maroonb0,#B03060\n"+
						"gray69,#B0B0B0\n"+
						"lightsteelblue,#B0C4DE\n"+
						"powderblue,#B0E0E6\n"+
						"lightskyblue1,#B0E2FF\n"+
						"kidneybean,#B13E0F\n"+
						"firebrick,#B22222\n"+
						"darkorchid2,#B23AEE\n"+
						"purplefish,#B272A6\n"+
						"cappuccino,#B28647\n"+
						"shamrockshake,#B2D0B4\n"+
						"lightblue2,#B2DFEE\n"+
						"jonathanapple,#B3432B\n"+
						"gray70,#B3B3B3\n"+
						"keylimepie,#B3C95A\n"+
						"olivedrab2,#B3EE3A\n"+
						"mediumorchid3,#B452CD\n"+
						"lightcyan3,#B4CDCD\n"+
						"vanillamint,#B4D7BF\n"+
						"darkseagreen2,#B4EEB4\n"+
						"thistle,#B5509C\n"+
						"brass,#B5A642\n"+
						"gray71,#B5B5B5\n"+
						"harold'scrayon,#B62084\n"+
						"cranberry,#B6316C\n"+
						"cafeaulait,#B67C3D\n"+
						"titanium,#B6AFA9\n"+
						"brushedaluminum,#B6C5BE\n"+
						"heatherblue,#B7C3D0\n"+
						"new$20,#B7C8B6\n"+
						"redcoat,#B81324\n"+
						"copper,#B87333\n"+
						"darkgoldenrod,#B8860B\n"+
						"gray72,#B8B8B8\n"+
						"slategray2,#B9D3EE\n"+
						"mediumorchid,#BA55D3\n"+
						"anjoupear,#BAAF07\n"+
						"gray73,#BABABA\n"+
						"braeburnapple,#BB2A3C\n"+
						"paleturquoise1,#BBFFFF\n"+
						"coconutshell,#BC7642\n"+
						"rosybrown,#BC8F8F\n"+
						"lightsteelblue2,#BCD2EE\n"+
						"chartreuseverte,#BCE937\n"+
						"greenmist,#BCED91\n"+
						"darkolivegreen2,#BCEE68\n"+
						"purplecandy,#BDA0CB\n"+
						"darkkhaki,#BDB76B\n"+
						"gray74,#BDBDBD\n"+
						"mintgreen,#BDFCC9\n"+
						"strawberry,#BE2625\n"+
						"gray,#BEBEBE\n"+
						"cateye,#BEE554\n"+
						"darkorchid1,#BF3EFF\n"+
						"violetflower,#BF5FFF\n"+
						"gray75,#BFBFBF\n"+
						"lightblue1,#BFEFFF\n"+
						"silver,#C0C0C0\n"+
						"lichen,#C0D9AF\n"+
						"lightblue,#C0D9D9\n"+
						"olivedrab1,#C0FF3E\n"+
						"honeydew3,#C1CDC1\n"+
						"azure3,#C1CDCD\n"+
						"pastelblue,#C1F0F6\n"+
						"darkseagreen1,#C1FFC1\n"+
						"gray76,#C2C2C2\n"+
						"robin'segg,#C3E4ED\n"+
						"almond,#C48E48\n"+
						"gray77,#C4C4C4\n"+
						"sgibrightgray,#C5C1AA\n"+
						"minticecream,#C5E3BF\n"+
						"bacon,#C65D57\n"+
						"sgisalmon,#C67171\n"+
						"ash,#C6C3B5\n"+
						"slategray1,#C6E2FF\n"+
						"mediumvioletred,#C71585\n"+
						"chilipowder,#C73F17\n"+
						"redroof,#C75D4D\n"+
						"rawsienna,#C76114\n"+
						"crema,#C76E06\n"+
						"goldochre,#C77826\n"+
						"gray78,#C7C7C7\n"+
						"rubyred,#C82536\n"+
						"safetyvest,#C8F526\n"+
						"mochalatte,#C9AF94\n"+
						"gray79,#C9C9C9\n"+
						"lightsteelblue1,#CAE1FF\n"+
						"darkolivegreen1,#CAFF70\n"+
						"fog,#CBCAB6\n"+
						"grape,#CC00FF\n"+
						"bloodorange,#CC1100\n"+
						"orange,#CC3232\n"+
						"violetred,#CC3299\n"+
						"apple,#CC4E5C\n"+
						"ochre,#CC7722\n"+
						"gold5,#CC7F32\n"+
						"lavender,#CC99CC\n"+
						"ralphyellow,#CCCC00\n"+
						"gray80,#CCCCCC\n"+
						"offwhiteblue,#CCCCFF\n"+
						"offwhitegreen,#CCFFCC\n"+
						"red3,#CD0000\n"+
						"magenta3,#CD00CD\n"+
						"deeppink3,#CD1076\n"+
						"firebrick3,#CD2626\n"+
						"maroon3,#CD2990\n"+
						"violetred3,#CD3278\n"+
						"brown3,#CD3333\n"+
						"orangered3,#CD3700\n"+
						"tomato3,#CD4F39\n"+
						"indianred3,#CD5555\n"+
						"coral3,#CD5B45\n"+
						"indianred,#CD5C5C\n"+
						"hotpink3,#CD6090\n"+
						"darkorange3,#CD6600\n"+
						"chocolate3,#CD661D\n"+
						"sienna3,#CD6839\n"+
						"palevioletred3,#CD6889\n"+
						"orchid3,#CD69C9\n"+
						"salmon3,#CD7054\n"+
						"gold6,#CD7F32\n"+
						"lightsalmon3,#CD8162\n"+
						"orange3,#CD8500\n"+
						"peru,#CD853F\n"+
						"lightpink3,#CD8C95\n"+
						"pink3,#CD919E\n"+
						"darkgoldenrod3,#CD950C\n"+
						"plum3,#CD96CD\n"+
						"goldenrod3,#CD9B1D\n"+
						"rosybrown3,#CD9B9B\n"+
						"burlywood3,#CDAA7D\n"+
						"bartlettpear,#CDAB2D\n"+
						"gold3,#CDAD00\n"+
						"peachpuff3,#CDAF95\n"+
						"navajowhite3,#CDB38B\n"+
						"thistle3,#CDB5CD\n"+
						"bisque3,#CDB79E\n"+
						"mistyrose3,#CDB7B5\n"+
						"wheat3,#CDBA96\n"+
						"lightgoldenrod3,#CDBE70\n"+
						"antiquewhite3,#CDC0B0\n"+
						"lavenderblush3,#CDC1C5\n"+
						"seashell3,#CDC5BF\n"+
						"khaki3,#CDC673\n"+
						"cornsilk3,#CDC8B1\n"+
						"lemonchiffon3,#CDC9A5\n"+
						"snow3,#CDC9C9\n"+
						"yellow3,#CDCD00\n"+
						"lightyellow3,#CDCDB4\n"+
						"ivory3,#CDCDC1\n"+
						"verylightgrey,#CDCDCD\n"+
						"firetruckgreen,#CDD704\n"+
						"iceberglettuce,#CDE472\n"+
						"greengrape,#CECC15\n"+
						"oldgold,#CFB53B\n"+
						"gray81,#CFCFCF\n"+
						"celery,#CFD784\n"+
						"camo1,#CFDBC5\n"+
						"violetred,#D02090\n"+
						"conch,#D0A9AA\n"+
						"battleship,#D0D2C4\n"+
						"greencard,#D0FAEE\n"+
						"mediumorchid2,#D15FEE\n"+
						"feldspar,#D19275\n"+
						"gray82,#D1D1D1\n"+
						"pear,#D1E231\n"+
						"lightcyan2,#D1EEEE\n"+
						"chocolate,#D2691E\n"+
						"tan,#D2B48C\n"+
						"pinkglass,#D3BECF\n"+
						"lightgrey,#D3D3D3\n"+
						"lightgray,#D3D3D3\n"+
						"venetianred,#D41A1F\n"+
						"barney,#D4318C\n"+
						"englishred,#D43D1A\n"+
						"chili,#D44942\n"+
						"gray83,#D4D4D4\n"+
						"limepulp,#D4ED91\n"+
						"orangecandy,#D5B77A\n"+
						"fujiapple,#D66F62\n"+
						"lemon,#D6C537\n"+
						"gray84,#D6D6D6\n"+
						"thistle,#D8BFD8\n"+
						"wheat,#D8D8BF\n"+
						"coolcopper,#D98719\n"+
						"brightgold,#D9D919\n"+
						"gray85,#D9D9D9\n"+
						"quartz,#D9D9F3\n"+
						"orchid,#DA70D6\n"+
						"goldenrod,#DAA520\n"+
						"blueice,#DAF4F0\n"+
						"permanentredviolet,#DB2645\n"+
						"brownmadder,#DB2929\n"+
						"palevioletred,#DB7093\n"+
						"orchid,#DB70DB\n"+
						"tan,#DB9370\n"+
						"ham,#DB9EA6\n"+
						"goldenrod,#DBDB70\n"+
						"gray86,#DBDBDB\n"+
						"moon,#DBE6E0\n"+
						"mintblue,#DBFEF8\n"+
						"crimson,#DC143C\n"+
						"kumquat,#DC8909\n"+
						"pinkcandy,#DCA2CD\n"+
						"gainsboro,#DCDCDC\n"+
						"signorange,#DD7500\n"+
						"plum,#DDA0DD\n"+
						"carnation,#DE85B1\n"+
						"burlywood,#DEB887\n"+
						"gray87,#DEDEDE\n"+
						"cashew,#DFAE74\n"+
						"melonrindgreen,#DFFFA5\n"+
						"soylentred,#E04006\n"+
						"pinkjeep,#E0427F\n"+
						"mediumorchid1,#E066FF\n"+
						"yellowperch,#E0D873\n"+
						"stainlesssteel,#E0DFDB\n"+
						"gray88,#E0E0E0\n"+
						"honeydew2,#E0EEE0\n"+
						"azure2,#E0EEEE\n"+
						"lightcyan,#E0FFFF\n"+
						"pecan,#E18E2E\n"+
						"creamcitybrick,#E2DDB5\n"+
						"geraniumlake,#E31230\n"+
						"cadmiumreddeep,#E3170D\n"+
						"alizarincrimson,#E32636\n"+
						"madderlakedeep,#E32E30\n"+
						"rosemadder,#E33638\n"+
						"hematite,#E35152\n"+
						"marsyellow,#E3701A\n"+
						"yellowochre,#E38217\n"+
						"melon,#E3A869\n"+
						"banana,#E3CF57\n"+
						"gray89,#E3E3E3\n"+
						"mandarianorange,#E47833\n"+
						"beer,#E5BC3B\n"+
						"gray90,#E5E5E5\n"+
						"semisweetchocolate2,#E6B426\n"+
						"lavender,#E6E6FA\n"+
						"silver,#E6E8FA\n"+
						"tongue,#E79EA9\n"+
						"espresso,#E7C6A5\n"+
						"darkwheat,#E8C782\n"+
						"gray91,#E8E8E8\n"+
						"chrome,#E8F1D4\n"+
						"darksalmon,#E9967A\n"+
						"lightwood,#E9C2A6\n"+
						"plum2,#EAADEA\n"+
						"strawberrysmoothie,#EAB5C5\n"+
						"mediumgoldenrod,#EAEAAE\n"+
						"cherry,#EB5E66\n"+
						"newtan,#EBC79E\n"+
						"pistachioshell,#EBCEAC\n"+
						"gray92,#EBEBEB\n"+
						"pigletsnout,#ECC3BF\n"+
						"corfupink,#ECC8EC\n"+
						"carrot,#ED9121\n"+
						"lightcopper,#EDC393\n"+
						"goldendeliciousapple,#EDCB62\n"+
						"gray93,#EDEDED\n"+
						"red2,#EE0000\n"+
						"magenta2,#EE00EE\n"+
						"deeppink2,#EE1289\n"+
						"firebrick2,#EE2C2C\n"+
						"maroon2,#EE30A7\n"+
						"violetred2,#EE3A8C\n"+
						"brown2,#EE3B3B\n"+
						"orangered2,#EE4000\n"+
						"tomato2,#EE5C42\n"+
						"indianred2,#EE6363\n"+
						"coral2,#EE6A50\n"+
						"hotpink2,#EE6AA7\n"+
						"darkorange2,#EE7600\n"+
						"chocolate2,#EE7621\n"+
						"sienna2,#EE7942\n"+
						"palevioletred2,#EE799F\n"+
						"orchid2,#EE7AE9\n"+
						"salmon2,#EE8262\n"+
						"violet,#EE82EE\n"+
						"tan,#EE8833\n"+
						"lightsalmon2,#EE9572\n"+
						"orange2,#EE9A00\n"+
						"tan2,#EE9A49\n"+
						"lightpink2,#EEA2AD\n"+
						"pink2,#EEA9B8\n"+
						"darkgoldenrod2,#EEAD0E\n"+
						"plum2,#EEAEEE\n"+
						"goldenrod2,#EEB422\n"+
						"rosybrown2,#EEB4B4\n"+
						"burlywood2,#EEC591\n"+
						"gold2,#EEC900\n"+
						"peachpuff2,#EECBAD\n"+
						"navajowhite2,#EECFA1\n"+
						"thistle2,#EED2EE\n"+
						"bisque2,#EED5B7\n"+
						"mistyrose2,#EED5D2\n"+
						"beachsand,#EED6AF\n"+
						"wheat2,#EED8AE\n"+
						"lightgoldenrod2,#EEDC82\n"+
						"lightgoldenrod,#EEDD82\n"+
						"antiquewhite2,#EEDFCC\n"+
						"lavenderblush2,#EEE0E5\n"+
						"seashell2,#EEE5DE\n"+
						"khaki2,#EEE685\n"+
						"palegoldenrod,#EEE8AA\n"+
						"cornsilk2,#EEE8CD\n"+
						"lemonchiffon2,#EEE9BF\n"+
						"snow2,#EEE9E9\n"+
						"yellowcandy,#EEEB8D\n"+
						"yellow2,#EEEE00\n"+
						"lightyellow2,#EEEED1\n"+
						"ivory2,#EEEEE0\n"+
						"lightcoral,#F08080\n"+
						"pyridiumorange,#F0A804\n"+
						"khaki,#F0E68C\n"+
						"gray94,#F0F0F0\n"+
						"aliceblue,#F0F8FF\n"+
						"honeydew,#F0FFF0\n"+
						"azure,#F0FFFF\n"+
						"watermelonpulp,#F2473F\n"+
						"gray95,#F2F2F2\n"+
						"grapefruit,#F3E88E\n"+
						"sandybrown,#F4A460\n"+
						"soylentyellow,#F4F776\n"+
						"cranberryjello,#F54D70\n"+
						"pummelopulp,#F5785A\n"+
						"wheat,#F5DEB3\n"+
						"beige,#F5F5DC\n"+
						"whitesmoke,#F5F5F5\n"+
						"mintcream,#F5FFFA\n"+
						"pomegranate,#F64D54\n"+
						"dogtongue,#F6A4D5\n"+
						"pinkcloud,#F6A8B6\n"+
						"bermudasand,#F6C9CC\n"+
						"pinkshell,#F6CCDA\n"+
						"cottoncandy,#F7B3DA\n"+
						"gray97,#F7F7F7\n"+
						"orangecrush,#F87531\n"+
						"ghostwhite,#F8F8FF\n"+
						"raspberryred,#FA1D2F\n"+
						"salmon,#FA8072\n"+
						"cantaloupepulp,#FA9A50\n"+
						"antiquewhite,#FAEBD7\n"+
						"linen,#FAF0E6\n"+
						"lightgoldenrodyellow,#FAFAD2\n"+
						"gray98,#FAFAFA\n"+
						"apricot,#FBA16C\n"+
						"gummiyellow,#FBDB0C\n"+
						"corn,#FBEC5D\n"+
						"gummired,#FC1501\n"+
						"packergold,#FCB514\n"+
						"signyellow,#FCD116\n"+
						"bread,#FCD59C\n"+
						"pineapple,#FCDC3B\n"+
						"eggshell,#FCE6C9\n"+
						"gray99,#FCFCFC\n"+
						"titaniumwhite,#FCFFF0\n"+
						"oldlace,#FDF5E6\n"+
						"zincwhite,#FDF8FF\n"+
						"honey,#FEE5AC\n"+
						"desertsand,#FEE8D6\n"+
						"peach,#FEF0DB\n"+
						"buttermilk,#FEF1B5\n"+
						"red,#FF0000\n"+
						"brightred,#FF0033\n"+
						"broadwaypink,#FF0066\n"+
						"orangered,#FF007F\n"+
						"fuchsia2,#FF00AA\n"+
						"rose,#FF00CC\n"+
						"fuchsia,#FF00FF\n"+
						"magenta,#FF00FF\n"+
						"cadmiumredlight,#FF030D\n"+
						"deeppink,#FF1493\n"+
						"spicypink,#FF1CAE\n"+
						"orangered,#FF2400\n"+
						"firebrick1,#FF3030\n"+
						"nectarine,#FF3300\n"+
						"novascotiasalmon,#FF3333\n"+
						"maroon1,#FF34B3\n"+
						"greenishumber,#FF3D0D\n"+
						"violetred1,#FF3E96\n"+
						"orangered4,#FF4040\n"+
						"orangered,#FF4500\n"+
						"safetycone,#FF5333\n"+
						"fleshochre,#FF5721\n"+
						"cadmiumorange,#FF6103\n"+
						"tomato,#FF6347\n"+
						"orange,#FF6600\n"+
						"seattlesalmon,#FF6666\n"+
						"hotpink,#FF69B4\n"+
						"indianred1,#FF6A6A\n"+
						"hotpink1,#FF6EB4\n"+
						"neonpink,#FF6EC7\n"+
						"tangerine,#FF7216\n"+
						"coral1,#FF7256\n"+
						"oregonsalmon,#FF7722\n"+
						"orange5,#FF7D40\n"+
						"darkorange1,#FF7F00\n"+
						"chocolate1,#FF7F24\n"+
						"coral,#FF7F50\n"+
						"orange,#FF8000\n"+
						"sienna1,#FF8247\n"+
						"palevioletred1,#FF82AB\n"+
						"orchid1,#FF83FA\n"+
						"darkorange5,#FF8600\n"+
						"darkorange,#FF8C00\n"+
						"salmon1,#FF8C69\n"+
						"bubblegum,#FF92BB\n"+
						"cadmiumyellow,#FF9912\n"+
						"peach,#FF9955\n"+
						"lightsalmon,#FFA07A\n"+
						"orange,#FFA500\n"+
						"tan1,#FFA54F\n"+
						"naplesyellowdeep,#FFA812\n"+
						"aureolineyellow,#FFA824\n"+
						"gold7,#FFAA00\n"+
						"peachpuff,#FFADB9\n"+
						"lightpink1,#FFAEB9\n"+
						"cadmiumyellowlight,#FFB00F\n"+
						"pink1,#FFB5C5\n"+
						"lightpink,#FFB6C1\n"+
						"darkgoldenrod1,#FFB90F\n"+
						"plum1,#FFBBFF\n"+
						"pink,#FFC0CB\n"+
						"goldenrod1,#FFC125\n"+
						"rosybrown1,#FFC1C1\n"+
						"cheddar,#FFC469\n"+
						"mustard,#FFCC11\n"+
						"sand,#FFCC99\n"+
						"flatpink,#FFCCCC\n"+
						"burlywood1,#FFD39B\n"+
						"gold,#FFD700\n"+
						"peachpuff,#FFDAB9\n"+
						"navajowhite,#FFDEAD\n"+
						"thistle1,#FFE1FF\n"+
						"cadmiumlemon,#FFE303\n"+
						"moccasin,#FFE4B5\n"+
						"bisque,#FFE4C4\n"+
						"mistyrose,#FFE4E1\n"+
						"yolk,#FFE600\n"+
						"wheat1,#FFE7BA\n"+
						"blanchedalmond,#FFEBCD\n"+
						"lightgoldenrod1,#FFEC8B\n"+
						"papayawhip,#FFEFD5\n"+
						"antiquewhite1,#FFEFDB\n"+
						"lavenderblush,#FFF0F5\n"+
						"seashell,#FFF5EE\n"+
						"khaki1,#FFF68F\n"+
						"cornsilk,#FFF8DC\n"+
						"lemonchiffon,#FFFACD\n"+
						"floralwhite,#FFFAF0\n"+
						"snow,#FFFAFA\n"+
						"coconut,#FFFCCF\n"+
						"yellow,#FFFF00\n"+
						"papaya,#FFFF7E\n"+
						"popcornyellow,#FFFFAA\n"+
						"bone,#FFFFCC\n"+
						"lightyellow,#FFFFE0\n"+
						"ivory,#FFFFF0\n"+
						"white,#FFFFFF";
		String[] bigColorLines = bigColorList.split("\n");
		for (String bcl:bigColorLines) {
			String[] bclChunks = bcl.split(",");
			kaleidoColors.put(bclChunks[0].toLowerCase(),bclChunks[1].toLowerCase());
		}

	}
	
 	public void run(String arg) {
		showDialog();
	}

	void showDialog() {
		Color fc =Toolbar.getForegroundColor();
		String fname = getColorName(fc, "white");
		Color bc =Toolbar.getBackgroundColor();
		String bname = getColorName(bc, "black");
		Color sc =Roi.getColor();
		String sname = getColorName(sc, "yellow");
		GenericDialog gd = new GenericDialog("Colors");
		gd.addChoice("Foreground:", colors, fname);
		gd.addChoice("Background:", colors, bname);
		gd.addChoice("Selection:", colors, sname);
		Vector choices = gd.getChoices();
		fchoice = (Choice)choices.elementAt(0);
		bchoice = (Choice)choices.elementAt(1);
		schoice = (Choice)choices.elementAt(2);
		fchoice.addItemListener(this);
		bchoice.addItemListener(this);
		schoice.addItemListener(this);
		
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (fc2!=fc) Toolbar.setForegroundColor(fc);
			if (bc2!=bc) Toolbar.setBackgroundColor(bc);
			if (sc2!=sc) {
				Roi.setColor(sc);
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp!=null && imp.getRoi()!=null) imp.draw();
			}
			return;
		}
		fname = gd.getNextChoice();
		bname = gd.getNextChoice();
		sname = gd.getNextChoice();
		fc2 = getColor(fname, Color.white);
		bc2 = getColor(bname, Color.black);
		sc2 = getColor(sname, Color.yellow);
		if (fc2!=fc) Toolbar.setForegroundColor(fc2);
		if (bc2!=bc) Toolbar.setBackgroundColor(bc2);
		if (sc2!=sc) {
			Roi.setColor(sc2);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.draw();
			Toolbar.getInstance().repaint();
		}
	}
	
	public static String getColorName(Color c, String defaultName) {
		if (c==null) return defaultName;
		String name = defaultName;
		if (name!=null && name.length()>0 && Character.isUpperCase(name.charAt(0))) {
			if (c.equals(Color.red)) name = colors2[0];
			else if (c.equals(Color.green)) name = colors2[1];
			else if (c.equals(Color.blue)) name = colors2[2];
			else if (c.equals(Color.magenta)) name = colors2[3];
			else if (c.equals(Color.cyan)) name = colors2[4];
			else if (c.equals(Color.yellow)) name = colors2[5];
			else if (c.equals(Color.orange)) name = colors2[6];
			else if (c.equals(Color.black)) name = colors2[7];
			else if (c.equals(Color.white)) name = colors2[8];
		} else {
			if (c.equals(Color.red)) name = colors[0];
			else if (c.equals(Color.green)) name = colors[1];
			else if (c.equals(Color.blue)) name = colors[2];
			else if (c.equals(Color.magenta)) name = colors[3];
			else if (c.equals(Color.cyan)) name = colors[4];
			else if (c.equals(Color.yellow)) name = colors[5];
			else if (c.equals(Color.orange)) name = colors[6];
			else if (c.equals(Color.black)) name = colors[7];
			else if (c.equals(Color.white)) name = colors[8];
		}
		return name;
	}
	
	public static Color getColor(String name, Color defaultColor) {
		if (name==null) return defaultColor;
		else if (name.matches("#......(..)?"))
			return Colors.decode(name, Color.gray);
		else {
			name = name.toLowerCase(Locale.US);

			Color c = defaultColor;
			if (name.toLowerCase().equals(colors[0])) c = Color.red;
			else if (name.toLowerCase().equals(colors[1])) c = Color.green;
			else if (name.toLowerCase().equals(colors[2])) c = Color.blue;
			else if (name.toLowerCase().equals(colors[3])) c = Color.magenta;
			else if (name.toLowerCase().equals(colors[4])) c = Color.cyan;
			else if (name.toLowerCase().equals(colors[5])) c = Color.yellow;
			else if (name.toLowerCase().equals(colors[6])) c = Color.orange;
			else if (name.toLowerCase().equals(colors[7])) c = Color.black;
			else if (name.toLowerCase().equals(colors[8])) c = Color.white;
			if (c == defaultColor) {
				String colorHex = kaleidoColors.get(name.toLowerCase());
				if (colorHex!=null)
					c = decode(colorHex, defaultColor);
			}
			return c;
		}
	}

	public static Color decode(String hexColorString, Color defaultColor) {
		Color color = defaultColor;
		if (hexColorString.matches("#......(..)?")) {
			hexColorString = hexColorString.substring(1);
			int len = hexColorString.length();
			if (!(len==6 || len==8)) {
				return defaultColor;
				//				hexColorString = hexColorString + IJ.pad(0, 8-hexColorString.length());
				//				
				////				hexColor = hexColor+defaultHexString.substring(hexColor.length()+1, defaultHexString.length());
			}
			float alpha = len==8?parseHex(hexColorString.substring(0,2)):1f;
			if (len==8)
				hexColorString = hexColorString.substring(2);
			float red = parseHex(hexColorString.substring(0,2));
			float green = parseHex(hexColorString.substring(2,4));
			float blue = parseHex(hexColorString.substring(4,6));
			color = new Color(red, green, blue, alpha);
		} else {
			color = getColor(hexColorString, defaultColor);
		}
		return color;
	}

	public static int getRed(String hexColor) {
		return decode(hexColor, Color.black).getRed();
	}

	public static int getGreen(String hexColor) {
		return decode(hexColor, Color.black).getGreen();
	}

	public static int getBlue(String hexColor) {
		return decode(hexColor, Color.black).getBlue();
	}

	/** Converts a hex color (e.g., "ffff00") into "red", "green", "yellow", etc.
		Returns null if the color is not one of the eight primary colors. */
	public static String hexToColor(String hex) {
		if (hex==null) return null;
		if (hex.startsWith("#"))
			hex = hex.substring(1);
		String color = null;
		if (hex.equals("ff0000")) color = "red";
		else if (hex.equals("00ff00")) color = "green";
		else if (hex.equals("0000ff")) color = "blue";
		else if (hex.equals("000000")) color = "black";
		else if (hex.equals("ffffff")) color = "white";
		else if (hex.equals("ffff00")) color = "yellow";
		else if (hex.equals("00ffff")) color = "cyan";
		else if (hex.equals("ff00ff")) color = "magenta";
		return color;
	}
	
	/** Converts a hex color (e.g., "ffff00") into "Red", "Green", "Yellow", etc.
		Returns null if the color is not one of the eight primary colors. */
	public static String hexToColor2(String hex) {
		if (hex==null) return null;
		if (hex.startsWith("#"))
			hex = hex.substring(1);
		String color = null;
		if (hex.equals("ff0000")) color = "Red";
		else if (hex.equals("00ff00")) color = "Green";
		else if (hex.equals("0000ff")) color = "Blue";
		else if (hex.equals("000000")) color = "Black";
		else if (hex.equals("ffffff")) color = "White";
		else if (hex.equals("ffff00")) color = "Yellow";
		else if (hex.equals("00ffff")) color = "Cyan";
		else if (hex.equals("ff00ff")) color = "Magenta";
		else if (hex.equals("ffc800")) color = "Orange";
		return color;
	}

	/** Converts a Color into a string ("red", "green", #aa55ff, etc.). */
	public static String colorToString(Color color) {
		String str = color!=null?"#"+Integer.toHexString(color.getRGB()):"none";
		if (str.length()==9 && str.startsWith("#ff"))
			str = "#"+str.substring(3);
		String str2 = hexToColor(str);   //this is not a helpful function.
		return str2!=null?str:str;
	}

	/** Converts a Color into a string ("Red", "Green", #aa55ff, etc.). */
	public static String colorToString2(Color color) {
		String str = color!=null?"#"+Integer.toHexString(color.getRGB()):"None";
		if (str.length()==9 && str.startsWith("#ff"))
			str = "#"+str.substring(3);
		String str2 = hexToColor2(str);   //this is not a helpful function.
		return str2!=null?str:str;
	}

	/** Converts a Color into a string ("#ff0000", "#00ff00", #aa55ff, etc.). */
	public static String colorToHexString(Color color) {
		String str = color!=null?"#"+Integer.toHexString(color.getRGB()):"#00000000";
//		if (str.length()==9 && str.startsWith("#ff"))
//			str = "#"+str.substring(3);
		if (str.length()==7)
		str = "#ff"+str.substring(1);
		return str;
	}

	private static float parseHex(String hex) {
		float value = 0f;
		try {value=Integer.parseInt(hex,16);}
		catch(Exception e) { }
		return value/255f;
	}

	public void itemStateChanged(ItemEvent e) {
		Choice choice = (Choice)e.getSource();
		String item = choice.getSelectedItem();
		Color color = getColor(item, Color.black);
		if (choice==fchoice)
			Toolbar.setForegroundColor(color);
		else if (choice==bchoice)
			Toolbar.setBackgroundColor(color);
		else if (choice==schoice) {
			Roi.setColor(color);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null && imp.getRoi()!=null) imp.draw();
			Toolbar.getInstance().repaint();
		}
	}
	
	public static String[] getColors(String... moreColors) {
		ArrayList names = new ArrayList();
		for (String arg: moreColors) {
			if (arg!=null && arg.length()>0 && (!Character.isLetter(arg.charAt(0))||arg.equals("None")))
				names.add(arg);
		}
		for (String arg: colors2)
			names.add(arg);
		return (String[])names.toArray(new String[names.size()]);
	}

}
