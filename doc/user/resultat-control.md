Jag gör kontrollen i två nivåer.

Först en geometrisk kontroll av tågen: att TrainUp och TrainDown rör sig inom samma fysiska intervall, i motsatta riktningar, med rimliga stopp och utan hopp eller positioner utanför nätet. Den delen ser redan bra ut i dina diagram.

Sedan gör jag en elektrisk kontroll i några utvalda tidpunkter, särskilt runt mötet vid ungefär t=290 s och under traction/bromsning. Då läser jag ur longtable för båda tågen p_req_W, p_W, u_V, i_A och för SS0–SS4 u_V, i_A, p_W, state. Jag kollar att tecken och storlekar hänger ihop, att blockerade diodstationer inte levererar effekt, att regen begränsas rimligt vid hög spänning och att den globala effektbalansen är plausibel.

För just energibalansen använder jag i princip:

$$ \sum P_\text{substation} - \sum P_\text{train} \approx P_\text{förluster} > 0 $$

där regenererande tåg har negativ p_W. Om exempelvis alla stationer blockerar måste de bromsande tågen kunna försörja de motordrivande tågen plus nätförlusterna; annars är resultatet omöjligt.