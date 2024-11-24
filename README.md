# InstagramPostScraper
A Kotlin class to download videos, images, stories and highlights from Instagram.

<h2>dependencies</h2>
<code>JDK 8+</code>
<br><br>

  > [!NOTE]\
  > Currently tested in\
  > Openjdk 21.0.4 2024-07-16 LTS\
  > Gradle 8.10.1
<br>

<h2>run it</h2>
set your instagram post url in Main.kt, then make sure that gradlew have exec permissions.<br>
if you pretened download stories or highlights, set your user and password in Main.kt
<br><br>
<url>
  <li> Linux & macOS </li><br>
  <pre><code>./gradlew run</code></pre>
  <li> Windows </li><br>
  <pre><code>gradlew.bat run</code></pre>
</ul>
<br><br>

>[!WARNING]\
>Accounts used with the scraper are quite susceptible to suspension. <b>Do not use your personal account</b>.<br><br>
>And when u run this scraper from a datacenter (even smaller ones), chances are large you will not pass. Also, if your ip reputation at home is low, you won't pass
 <br>
