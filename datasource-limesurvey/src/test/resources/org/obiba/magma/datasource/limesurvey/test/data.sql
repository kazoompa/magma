DROP TABLE IF EXISTS test_survey_65284;
CREATE TABLE IF NOT EXISTS test_survey_65284 (
 id INTEGER GENERATED BY DEFAULT AS IDENTITY NOT NULL,
 submitdate datetime DEFAULT NULL,
 lastpage INTEGER DEFAULT NULL,
 startlanguage varchar(20) NOT NULL,
 token varchar(36) DEFAULT NULL,
 65284X1X1 varchar(5) DEFAULT NULL,
 65284X1X1other varchar(10000),
 65284X1X2 varchar(10000),
 65284X1X8SQ001 varchar(5) DEFAULT NULL,
 65284X1X8SQ002 varchar(5) DEFAULT NULL,
 65284X1X8SQ003 varchar(5) DEFAULT NULL,
 65284X1X9SQ001 varchar(5) DEFAULT NULL,
 65284X1X9SQ001comment varchar(10000),
 65284X1X9SQ002 varchar(5) DEFAULT NULL,
 65284X1X9SQ002comment varchar(10000),
 65284X1X10 varchar(5) DEFAULT NULL,
 65284X1X10comment varchar(10000),
 65284X1X11SQ001 double DEFAULT NULL,
 65284X1X11SQ002 double DEFAULT NULL,
 65284X1X121 varchar(5) DEFAULT NULL,
 65284X1X122 varchar(5) DEFAULT NULL,
 65284X1X13 varchar(1) DEFAULT NULL,
 65284X1X16 varchar(1) DEFAULT NULL,
 65284X1X17 varchar(5) DEFAULT NULL,
 65284X1X17other varchar(10000),
 65284X1X18 date DEFAULT NULL,
 65284X2X3SQ001 varchar(5) DEFAULT NULL,
 65284X2X3SQ002 varchar(5) DEFAULT NULL,
 65284X2X4 varchar(10000),
 65284X2X4_filecount tinyint(4) DEFAULT NULL,
 65284X2X5SQY1_SQX1 varchar(10000),
 65284X2X5SQY1_SQX2 varchar(10000),
 65284X2X5SQY2_SQX1 varchar(10000),
 65284X2X5SQY2_SQX2 varchar(10000),
 65284X2X6SQY1_SQX1 varchar(10000),
 65284X2X6SQY1_SQX2 varchar(10000),
 65284X2X6SQY2_SQX1 varchar(10000),
 65284X2X6SQY2_SQX2 varchar(10000),
 65284X2X7SQ001#0 varchar(5) DEFAULT NULL,
 65284X2X7SQ001#1 varchar(5) DEFAULT NULL,
 65284X2X7SQ002#0 varchar(5) DEFAULT NULL,
 65284X2X7SQ002#1 varchar(5) DEFAULT NULL,
 65284X2X14SQ001 varchar(5) DEFAULT NULL,
 65284X2X15SQ001 varchar(5) DEFAULT NULL,
  PRIMARY KEY (id)
)

INSERT INTO test_survey_65284 (id, submitdate, lastpage, startlanguage, token, 65284X1X40, 65284X1X1, 65284X1X1other, 65284X1X2, 65284X1X8SQ001, 65284X1X8SQ002, 65284X1X8SQ003, 65284X1X9SQ001, 65284X1X9SQ001comment, 65284X1X9SQ002, 65284X1X9SQ002comment, 65284X1X10, 65284X1X10comment, 65284X1X11SQ001, 65284X1X11SQ002, 65284X1X121, 65284X1X122, 65284X1X13, 65284X1X16, 65284X1X17, 65284X1X17other, 65284X1X18, 65284X2X3SQ001, 65284X2X3SQ002, 65284X2X4, 65284X2X4_filecount, 65284X2X5SQY1_SQX1, 65284X2X5SQY1_SQX2, 65284X2X5SQY2_SQX1, 65284X2X5SQY2_SQX2, 65284X2X6SQY1_SQX1, 65284X2X6SQY1_SQX2, 65284X2X6SQY2_SQX1, 65284X2X6SQY2_SQX2, 65284X2X7SQ001#0, 65284X2X7SQ001#1, 65284X2X7SQ002#0, 65284X2X7SQ002#1, 65284X2X14SQ001, 65284X2X15SQ001) VALUES
(7, '2012-03-20 14:30:03', 2, 'en', 'tok', '3', 'A2', '', 'hfghgf', '', 'Y', '', 'Y', '', '', '', 'A1', '', NULL, NULL, 'A2', 'A1', 'N', 'M', 'A1', '', '2012-03-21', '1', '2', '', 0, 'fgh', 'hgf', 't', 'r', '222', '', '', '322', '', 'A1', '', 'A1', 'S', '');
