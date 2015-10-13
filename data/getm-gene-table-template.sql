CREATE TABLE  `ner_genes` (
  `document` int(32) default NULL,
  `entity` int(32) default NULL,
  `start` int(32) default NULL,
  `end` int(32) default NULL,
  `text` varchar(255) default NULL,
  `comment` varchar(255) default NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1;