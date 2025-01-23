package commonResources;

public class UnsafeFieldUsage5 {

    private final Vehicle vehicle = new Vehicle();

    public UnsafeFieldUsage5() {
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                vehicle.setModel("Toyota");
            }
        });
        t1.start();

        Thread t2 = new Thread(this::createVehicle);
        t2.start();
    }

    private void createVehicle() {
        vehicle.setModel("Honda");
        vehicle.setYear(2020);
        vehicle.setColor("Red");
        vehicle.setPrice(20000);
    }
}
