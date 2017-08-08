<?php
error_reporting(E_ALL ^ E_WARNING ^ E_NOTICE);
//this file is for testing connection only, to confirm if password has been inputted correctly
$servername = "localhost";
$username = "id1051297_centaliu";
$password = $_GET["dbpwd"];
$dbname = "id1051297_dbappdata";
// Create connection
$conn = new mysqli($servername, $username, $password, $dbname);
// Check connection
if ($conn->connect_error) {
    echo "FAIL";
} else {
    echo "OK";
    $conn->close();
}
?>