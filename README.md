# DictionaryClient
This repository contains the code for a client application that connects to a DICT.org server.

The Dictionary.jar file is created with the make command. To run the graphical interface you run:

`$ make run`

To use the client itself simply pick a database and matching strategy from their respective dropdown lists, then type the word you are looking for in the search bar. The client will then return the definitions based on the database(s) that you picked.

The * database tells the client to search and return definitions from all databases, while the ! tells the client to return only the first definition it found.

A matching strategy refers to how the auto-complete feature works when typing in the word.
