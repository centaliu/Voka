<?php
$servername = "localhost";
$username = "id1051297_centaliu";
$password = $_GET["dbpwd"];
$dbname = "id1051297_dbappdata";
// Create connection
$conn = new mysqli($servername, $username, $password, $dbname);
// Check connection
if ($conn->connect_error) {
    die("Connection failed: " . $conn->connect_error);
} 
$sqlstr = "select VocDesc from tblvocbank where Voc = '".$_GET["voc"]."' limit 1";
$result = $conn->query($sqlstr);
if ($result->num_rows > 0) {
    // output data of each row
    while($row = $result->fetch_assoc()) {
        echo $row["VocDesc"];
    }
} else {
    echo "";
}
$conn->close();
?>